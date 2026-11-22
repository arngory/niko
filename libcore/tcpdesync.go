package libcore

import (
	"encoding/json"

	"github.com/sagernet/sing-box/common/tcpdesync"
)

// TcpDesyncCapabilities: what this platform lets an unprivileged process do for «Обход DPI для прямых соединений»
// (docs/design/dpi.md §5.1), as JSON {"ttl", "oob", "notsent", "error"}: the core probes IP_TTL, MSG_OOB and
// tcpi_notsent_bytes on a loopback connection once a process. The app offers the strategies they allow.
func TcpDesyncCapabilities() string {
	data, _ := json.Marshal(tcpdesync.Capabilities()) // bools and a string
	return string(data)
}
