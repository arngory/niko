package libcore

import (
	"crypto/x509"
	"log"
	_ "unsafe" // for go:linkname
)

//go:linkname systemRoots crypto/x509.systemRoots
var systemRoots *x509.CertPool

// updateRootCACerts adds the certificates of a user-imported ca.pem to the system roots. It never
// replaces them: a file that only holds a MITM root would otherwise become the sole trust anchor.
func updateRootCACerts(pem []byte) {
	roots, err := x509.SystemCertPool()
	if err != nil || roots == nil {
		roots = x509.NewCertPool()
	}
	if !roots.AppendCertsFromPEM(pem) {
		log.Println("failed to append certificates from pem")
		return
	}
	systemRoots = roots
	log.Println("imported ca.pem was added to the system roots")
}

//go:linkname initSystemRoots crypto/x509.initSystemRoots
func initSystemRoots()
