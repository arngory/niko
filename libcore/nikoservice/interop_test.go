package nikoservice

import (
	"context"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"libcore/nikopb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// TestNikodRaycontrolInterop is nikod against a real rayd (arngory/raycontrol), run by
// .github/workflows/raycontrol-interop.yml's job desktop: a running nikod in development mode at NIKOD_ADDRESS
// registers by the invite of RAYCONTROL_INVITE_FILE (from `rayctl device invite USER NAME -pin -o FILE`, its PIN in
// RAYCONTROL_PIN), fetches the node's subscription into the node's group, and connects through each of its profiles
// in proxy mode: a request through the local proxy reaches an HTTP server of the test's own on the runner, from the
// node's address RAYCONTROL_NODE, not from this machine's.
func TestNikodRaycontrolInterop(t *testing.T) {
	address := os.Getenv("NIKOD_ADDRESS")
	if address == "" {
		t.Skip("NIKOD_ADDRESS not set (raycontrol-interop.yml sets it)")
	}
	data, err := os.ReadFile(os.Getenv("RAYCONTROL_INVITE_FILE"))
	if err != nil {
		t.Fatal(err)
	}
	invite := strings.TrimSpace(string(data))
	pin, nodeAddress := os.Getenv("RAYCONTROL_PIN"), os.Getenv("RAYCONTROL_NODE")
	conn, err := grpc.NewClient(address, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	client := nikopb.NewNikoServiceClient(conn)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()

	info, err := client.GetInfo(ctx, &nikopb.GetInfoRequest{})
	if err != nil || info.Api < 11 {
		t.Fatalf("nikod: %v %v", info, err)
	}
	preview, err := client.Recognize(ctx, &nikopb.ImportInput{Text: invite})
	if err != nil || preview.Kind != nikopb.ImportPreview_NODE || !preview.Node.Invite {
		t.Fatalf("the invite: %v %v", preview, err)
	}
	t.Logf("the invite: host %s, name %q, PIN %v", preview.Node.Host, preview.Node.DnsName, preview.Node.PinRequired)
	run := func(pin string) *nikopb.TaskProgress {
		t.Helper()
		stream, err := client.Import(ctx, &nikopb.ImportRequest{Input: &nikopb.ImportInput{Text: invite}, Pin: pin})
		if err != nil {
			t.Fatal(err)
		}
		var last *nikopb.TaskProgress
		for {
			p, err := stream.Recv()
			if err == io.EOF {
				return last
			}
			if err != nil {
				t.Fatal(err)
			}
			t.Logf("import: %s", p.Line)
			last = p
		}
	}
	result := run("")
	if preview.Node.PinRequired {
		// the PIN asked for before any try, then the registration with it
		if result.Pin == nil || result.Pin.Left != -1 {
			t.Fatalf("no PIN asked for: %v", result)
		}
		if pin == "" {
			t.Fatal("the invite needs a PIN and RAYCONTROL_PIN has none")
		}
		result = run(pin)
	}
	if result.Code != 0 || result.NodeId == "" || result.GroupId == 0 || len(result.Ids) == 0 {
		t.Fatalf("the import: %v", result)
	}
	nodes, err := client.ListNodes(ctx, &nikopb.Empty{})
	if err != nil || len(nodes.Nodes) != 1 {
		t.Fatalf("the nodes: %v %v", nodes, err)
	}
	node := nodes.Nodes[0]
	if !node.Subscription || node.Updated == 0 || node.Expires <= node.Updated || node.NextUpdate == 0 || node.Refused {
		t.Fatalf("the node: %v", node)
	}
	t.Logf("node %s (%s): %d profiles, updated %d, expires %d, next %d, clock skew %d s", node.Name, node.Host,
		node.Profiles, node.Updated, node.Expires, node.NextUpdate, node.ClockSkew)
	rows, err := client.ListProfiles(ctx, &nikopb.ListProfilesRequest{GroupId: result.GroupId})
	if err != nil || len(rows.Profiles) == 0 {
		t.Fatalf("the rows: %v %v", rows, err)
	}

	// an HTTP server of the test's own, which the node's tunnels may reach (NIKO_SERVER_UNSAFE_ANY_DESTINATION)
	listener, err := net.Listen("tcp", "0.0.0.0:0")
	if err != nil {
		t.Fatal(err)
	}
	seen := make(chan string, 16)
	server := &http.Server{Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		host, _, _ := net.SplitHostPort(r.RemoteAddr)
		select {
		case seen <- host:
		default:
		}
		io.WriteString(w, "through the node")
	})}
	go server.Serve(listener)
	defer server.Close()
	target := "http://" + os.Getenv("RAYCONTROL_TARGET_HOST") + ":" + strconv.Itoa(listener.Addr().(*net.TCPAddr).Port) + "/"

	proxy, _ := url.Parse("http://127.0.0.1:2080")
	httpClient := &http.Client{Timeout: 15 * time.Second, Transport: &http.Transport{Proxy: http.ProxyURL(proxy),
		DisableKeepAlives: true}}
	var failed []string
	for _, row := range rows.Profiles {
		if row.NodeId != node.Id {
			t.Fatalf("a row without its node: %v", row)
		}
		proxyMode := nikopb.ConnectRequest_PROXY
		if _, err = client.Connect(ctx, &nikopb.ConnectRequest{ProfileId: row.Id, Mode: &proxyMode}); err != nil {
			failed = append(failed, row.Name+": Connect: "+err.Error())
			continue
		}
		var body string
		var getErr error
		deadline := time.Now().Add(45 * time.Second)
		for time.Now().Before(deadline) {
			var response *http.Response
			if response, getErr = httpClient.Get(target); getErr == nil {
				b, _ := io.ReadAll(response.Body)
				response.Body.Close()
				if body = string(b); response.StatusCode == http.StatusOK {
					break
				}
			}
			time.Sleep(time.Second)
		}
		if body != "through the node" {
			failed = append(failed, row.Name+" ("+row.Type+"): "+errText(getErr))
			continue
		}
		from := <-seen
		if nodeAddress != "" && from != nodeAddress {
			failed = append(failed, row.Name+": the request came from "+from+", not from the node")
			continue
		}
		t.Logf("%s (%s): through the node, from %s", row.Name, row.Type, from)
	}
	if len(failed) > 0 {
		t.Fatalf("not through the node:\n%s", strings.Join(failed, "\n"))
	}
}

func errText(err error) string {
	if err == nil {
		return "no answer of the test's server"
	}
	return err.Error()
}
