// Command quicbench measures HTTP/2 (TCP) and HTTP/3 (QUIC) downloads, straight or through a tunnel, for
// .github/workflows/quic-experiment.yml: does QUIC carried inside a TCP stream (VLESS/REALITY with XUDP, naive with
// UDP over TCP) lose to TCP on the same profile once the path has loss and delay?
//
//	quicbench serve -listen 127.0.0.1:19001,127.0.0.1:19002 -cert cert.pem -key key.pem
//	quicbench run -label "QUIC through REALITY" -proto h3 -addr 127.0.0.1:20001 -cert cert.pem
//
// serve answers GET /?n=N with N bytes, over HTTP/2 on TCP and HTTP/3 on UDP at each address, with a self-signed
// certificate for bench.test. run makes, per repetition, two fresh connections like a browser would: on the first a
// tiny request (connection setup and one round trip: "first") and then one large download ("bulk", Mbit/s), on the
// second a page of parallel requests ("page": setup included). It prints one Markdown row of the medians.
package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"errors"
	"flag"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"os"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/quic-go/quic-go"
	"github.com/quic-go/quic-go/http3"
)

const serverName = "bench.test"

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: quicbench serve|run [flags]")
		os.Exit(2)
	}
	var err error
	switch os.Args[1] {
	case "serve":
		err = serve(os.Args[2:])
	case "run":
		err = run(os.Args[2:])
	default:
		err = fmt.Errorf("unknown command %q", os.Args[1])
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "quicbench:", err)
		os.Exit(1)
	}
}

// the largest answer: a typo in -bulk must not stream forever
const maxAnswer = 256 << 20

func handler() http.Handler {
	block := make([]byte, 64<<10)
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		n, err := strconv.Atoi(r.URL.Query().Get("n"))
		if err != nil || n < 0 || n > maxAnswer {
			http.Error(w, "n", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Length", strconv.Itoa(n))
		w.Header().Set("Cache-Control", "no-store")
		for n > 0 {
			k := min(n, len(block))
			if _, err := w.Write(block[:k]); err != nil {
				return
			}
			n -= k
		}
	})
}

func selfSigned() (tls.Certificate, []byte, []byte, error) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return tls.Certificate{}, nil, nil, err
	}
	serial, err := rand.Int(rand.Reader, big.NewInt(1<<62))
	if err != nil {
		return tls.Certificate{}, nil, nil, err
	}
	tmpl := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: serverName},
		DNSNames:     []string{serverName},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(7 * 24 * time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		return tls.Certificate{}, nil, nil, err
	}
	keyDER, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		return tls.Certificate{}, nil, nil, err
	}
	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyDER})
	cert, err := tls.X509KeyPair(certPEM, keyPEM)
	return cert, certPEM, keyPEM, err
}

func serve(args []string) error {
	fs := flag.NewFlagSet("serve", flag.ExitOnError)
	listen := fs.String("listen", "", "comma-separated host:port: HTTP/2 on TCP and HTTP/3 on UDP at each")
	certOut := fs.String("cert", "", "where to write the certificate (PEM) the clients trust")
	keyOut := fs.String("key", "", "where to write its key (PEM), for a tunnel server that shows the same certificate")
	_ = fs.Parse(args)
	if *listen == "" || *certOut == "" {
		return errors.New("serve needs -listen and -cert")
	}
	cert, certPEM, keyPEM, err := selfSigned()
	if err != nil {
		return err
	}
	if err := os.WriteFile(*certOut, certPEM, 0o644); err != nil {
		return err
	}
	if *keyOut != "" {
		if err := os.WriteFile(*keyOut, keyPEM, 0o600); err != nil {
			return err
		}
	}
	h := handler()
	errs := make(chan error, 8)
	for _, addr := range strings.Split(*listen, ",") {
		ln, err := net.Listen("tcp", addr)
		if err != nil {
			return err
		}
		h2 := &http.Server{Handler: h, TLSConfig: &tls.Config{Certificates: []tls.Certificate{cert}, NextProtos: []string{"h2", "http/1.1"}}}
		go func() { errs <- h2.ServeTLS(ln, "", "") }()
		pc, err := net.ListenPacket("udp", addr)
		if err != nil {
			return err
		}
		h3 := &http3.Server{Handler: h, TLSConfig: http3.ConfigureTLSConfig(&tls.Config{Certificates: []tls.Certificate{cert}})}
		go func() { errs <- h3.Serve(pc) }()
	}
	fmt.Println("ready")
	return <-errs
}

// client is one browser-like connection: every request of it on one TCP or QUIC connection.
type client struct {
	get   func(ctx context.Context, n int) error
	close func()
}

func newClient(proto, addr string, roots *x509.CertPool) (*client, error) {
	tlsConf := &tls.Config{RootCAs: roots, ServerName: serverName}
	var rt http.RoundTripper
	var closeFn func()
	switch proto {
	case "h2":
		tr := &http.Transport{
			DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
				var d net.Dialer
				return d.DialContext(ctx, "tcp", addr)
			},
			TLSClientConfig:   tlsConf,
			ForceAttemptHTTP2: true,
			MaxConnsPerHost:   1,
		}
		rt, closeFn = tr, tr.CloseIdleConnections
	case "h3":
		tr := &http3.Transport{
			TLSClientConfig: tlsConf,
			Dial: func(ctx context.Context, _ string, tlsCfg *tls.Config, cfg *quic.Config) (*quic.Conn, error) {
				return quic.DialAddrEarly(ctx, addr, tlsCfg, cfg)
			},
		}
		rt, closeFn = tr, func() { _ = tr.Close() }
	default:
		return nil, fmt.Errorf("unknown -proto %q", proto)
	}
	hc := &http.Client{Transport: rt}
	get := func(ctx context.Context, n int) error {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://"+serverName+"/?n="+strconv.Itoa(n), nil)
		if err != nil {
			return err
		}
		resp, err := hc.Do(req)
		if err != nil {
			return err
		}
		defer resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("HTTP %d", resp.StatusCode)
		}
		got, err := io.Copy(io.Discard, resp.Body)
		if err != nil {
			return err
		}
		if got != int64(n) {
			return fmt.Errorf("%d bytes of %d", got, n)
		}
		return nil
	}
	return &client{get: get, close: closeFn}, nil
}

type sample struct {
	first, bulk, page time.Duration
	failed            []string
}

func once(proto, addr string, roots *x509.CertPool, bulk, pageN, pageSize int, timeout time.Duration) sample {
	var s sample
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	c, err := newClient(proto, addr, roots)
	if err != nil {
		s.failed = append(s.failed, err.Error())
		return s
	}
	start := time.Now()
	if err := c.get(ctx, 0); err != nil {
		s.failed = append(s.failed, "first: "+err.Error())
	} else {
		s.first = time.Since(start)
		start = time.Now()
		if err := c.get(ctx, bulk); err != nil {
			s.failed = append(s.failed, "bulk: "+err.Error())
		} else {
			s.bulk = time.Since(start)
		}
	}
	c.close()

	ctx2, cancel2 := context.WithTimeout(context.Background(), timeout)
	defer cancel2()
	c, err = newClient(proto, addr, roots)
	if err != nil {
		s.failed = append(s.failed, err.Error())
		return s
	}
	defer c.close()
	start = time.Now()
	var wg sync.WaitGroup
	var mu sync.Mutex
	var pageErr error
	for range pageN {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := c.get(ctx2, pageSize); err != nil {
				mu.Lock()
				if pageErr == nil {
					pageErr = err
				}
				mu.Unlock()
			}
		}()
	}
	wg.Wait()
	if pageErr != nil {
		s.failed = append(s.failed, "page: "+pageErr.Error())
	} else {
		s.page = time.Since(start)
	}
	return s
}

// median of the measured values (0: none measured) and how many of all failed
func median(v []time.Duration) time.Duration {
	if len(v) == 0 {
		return 0
	}
	sort.Slice(v, func(i, j int) bool { return v[i] < v[j] })
	return v[len(v)/2]
}

func cell(v []time.Duration, all int, f func(time.Duration) string) string {
	m := median(v)
	out := "fail"
	if m > 0 {
		out = f(m)
	}
	if failed := all - len(v); failed > 0 && m > 0 {
		out += fmt.Sprintf(" (%d/%d failed)", failed, all)
	}
	return out
}

func run(args []string) error {
	fs := flag.NewFlagSet("run", flag.ExitOnError)
	label := fs.String("label", "", "the row's name")
	proto := fs.String("proto", "h2", "h2 (TCP) or h3 (QUIC)")
	addr := fs.String("addr", "", "host:port the client connects to")
	certPath := fs.String("cert", "", "the server's certificate (PEM)")
	reps := fs.Int("reps", 3, "repetitions; the row has their medians")
	bulk := fs.Int("bulk", 4<<20, "bytes of the bulk download")
	pageN := fs.Int("page", 24, "parallel requests of the page")
	pageSize := fs.Int("page-size", 64<<10, "bytes of each page request")
	timeout := fs.Duration("timeout", 120*time.Second, "for each connection's requests")
	_ = fs.Parse(args)
	if *addr == "" || *certPath == "" || *bulk > maxAnswer || *pageSize > maxAnswer {
		return errors.New("run needs -addr and -cert, sizes within 256 MiB")
	}
	pemBytes, err := os.ReadFile(*certPath)
	if err != nil {
		return err
	}
	roots := x509.NewCertPool()
	if !roots.AppendCertsFromPEM(pemBytes) {
		return errors.New("no certificate in " + *certPath)
	}
	var first, bulkT, page []time.Duration
	for i := range *reps {
		s := once(*proto, *addr, roots, *bulk, *pageN, *pageSize, *timeout)
		for _, f := range s.failed {
			fmt.Fprintf(os.Stderr, "%s, repetition %d: %s\n", *label, i+1, f)
		}
		if s.first > 0 {
			first = append(first, s.first)
		}
		if s.bulk > 0 {
			bulkT = append(bulkT, s.bulk)
		}
		if s.page > 0 {
			page = append(page, s.page)
		}
	}
	ms := func(d time.Duration) string { return strconv.FormatInt(d.Milliseconds(), 10) }
	mbps := func(d time.Duration) string {
		return strconv.FormatFloat(float64(*bulk)*8/d.Seconds()/1e6, 'f', 1, 64)
	}
	fmt.Printf("| %s | %s | %s | %s |\n", *label, cell(first, *reps, ms), cell(bulkT, *reps, mbps), cell(page, *reps, ms))
	return nil
}
