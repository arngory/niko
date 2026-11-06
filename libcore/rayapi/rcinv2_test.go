package rayapi

import (
	"encoding/json"
	"strings"
	"testing"
)

// The interop test's reading of RCINV2: on the spec's vectors (api-v1 §3a, raycontrol TestInvite2Vectors), as the
// app's Rcinv2Test reads them: a failing interop then points at the node, not at the test.
func TestRcinv2JSONVectors(t *testing.T) {
	for _, c := range []struct {
		token, host string
		pin         bool
	}{
		{"AIA4WADRA4AACAQDAQCQMBYIBEFAWDANBYHQAAEGJQAAABSUEAQSEIZEEUTCOKBJFIVSYLJOF4YDCMRTGQ2TMNZYHE5DWPB5HY7VLEYLRMAIQAGOUCQ2FI5EUWTKOQCBIJBUIRKGI5EESSSLJRGU4T2QKFJFGVCVKZLVQWK2LNOF2XS7AAD3YVLAMFRGGZDFMZTWQ2LKNNWG23TPOBYXE43UOV3HO6DZPJ5XY7L6P5UVOCUA", "203.0.113.7", true},
		{"AIBCAAINXAAAAAAAAAAAAAAAAAAAOAABAIBQIBIGA4EASCQLBQGQ4DYAACDEYAAAAZKCAIJCEMSCKJRHFAUSUKZMFUXC6MBRGIZTINJWG44DSOR3HQ6T4P2VSMFYWAEIADHKBINCUOSKLJVHIBAUEQ2EIVDEOSCJJJFUYTKOJ5IFCUSTKRKVMV2YLFNFWXC5LZPQAB54KVQGCYTDMRSWMZ3INFVGW3DNNZXXA4LSON2HK5TXPB4XU634PV7H62KXBKAA", "2001:db8::7", false},
		{"AICBA3TPMRSS4ZLYMFWXA3DFFZRW63IAAEBAGBAFAYDQQCIKBMGA2DQPAAAIMTAAAADFIIBBEIRSIJJGE4UCSKRLFQWS4LZQGEZDGNBVGY3TQOJ2HM6D2PR7KWJQXCYARAAM5IFBUKR2JJNGU5AECQSDIRCUMR2IJFFEWTCNJZHVAUKSKNKFKVSXLBMVUW24LVPF6AAHXRKWAYLCMNSGKZTHNBUWU23MNVXG64DROJZXI5LWO54HS6T3PR6X473JK4FIA", "node.example.com", false},
	} {
		body, err := rcinv2JSON(c.token)
		if err != nil {
			t.Fatalf("%s: %v", c.host, err)
		}
		var got struct {
			Host string `json:"host"`
			SPA  struct {
				NodeID    string `json:"node_id"`
				Epoch     int    `json:"epoch_len_s"`
				Slot      int    `json:"slot_len_s"`
				PortBase  int    `json:"port_base"`
				PortRange int    `json:"port_range"`
				LenMin    int    `json:"len_min"`
				LenMax    int    `json:"len_max"`
			} `json:"spa"`
			DeviceID  string `json:"device_id"`
			DeviceKey string `json:"device_key"`
			Services  []struct {
				ID int `json:"id"`
			} `json:"services"`
			API struct {
				Port int    `json:"port"`
				Pin  string `json:"pin"`
			} `json:"api"`
			Expires int64 `json:"expires"`
			Pin     bool  `json:"pin"`
		}
		if err = json.Unmarshal(body, &got); err != nil {
			t.Fatal(err)
		}
		s := got.SPA
		if got.Host != c.host || got.Pin != c.pin || s.NodeID != "000102030405060708090a0b0c0d0e0f" || s.Epoch != 34380 ||
			s.Slot != 1620 || s.PortBase != 21907 || s.PortRange != 2955 || s.LenMin != 136 || s.LenMax != 206 ||
			got.DeviceID != "a0a1a2a3a4a5a6a7" || !strings.HasPrefix(got.DeviceKey, "404142") || len(got.Services) != 1 ||
			got.Services[0].ID != 7 || got.API.Port != 48213 || !strings.HasPrefix(got.API.Pin, "YGFiY2Rl") || got.Expires != 1767312000 {
			t.Fatalf("%s: %s", c.host, body)
		}
	}
	if _, err := rcinv2JSON("AIA4WADRA4AACAQDAQCQMBYIBEFAWDANBYHQAAEGJQAAABSUEAQSEIZEEUTCOKBJFIVSYLJOF4YDCMRTGQ2TMNZYHE5DWPB5HY7VLEYLRMAIQAGOUCQ2FI5EUWTKOQCBIJBUIRKGI5EESSSLJRGU4T2QKFJFGVCVKZLVQWK2LNOF2XS7AAD3YVLAMFRGGZDFMZTWQ2LKNNWG23TPOBYXE43UOV3HO6DZPJ5XY7L6P5UVOCUAAAAAAAAA"); err == nil {
		t.Fatal("bytes past its end taken")
	}
}
