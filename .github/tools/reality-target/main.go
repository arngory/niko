// reality-target: a TLS 1.3 server for interop jobs to stand behind REALITY as its target, a site's stand-in.
// It speaks Go's crypto/tls with its default groups (X25519MLKEM768 first, as Go fronts and most sites today), or
// X25519 alone with -classical, under a self-signed certificate for -name, and answers any request with 204.
//
//	reality-target -listen 127.0.0.1:14443 -name www.example.com [-classical]
package main

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"flag"
	"log"
	"math/big"
	"net/http"
	"time"
)

func main() {
	listen := flag.String("listen", "127.0.0.1:14443", "address to listen on")
	name := flag.String("name", "www.example.com", "the certificate's name")
	classical := flag.Bool("classical", false, "offer X25519 alone, as a site without X25519MLKEM768")
	flag.Parse()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		log.Fatal(err)
	}
	template := &x509.Certificate{
		SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: *name}, DNSNames: []string{*name},
		NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(30 * 24 * time.Hour),
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		log.Fatal(err)
	}
	config := &tls.Config{
		MinVersion:   tls.VersionTLS13,
		Certificates: []tls.Certificate{{Certificate: [][]byte{der}, PrivateKey: key}},
		NextProtos:   []string{"h2", "http/1.1"},
	}
	if *classical {
		config.CurvePreferences = []tls.CurveID{tls.X25519}
	}
	server := &http.Server{
		Addr:      *listen,
		TLSConfig: config,
		Handler: http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusNoContent)
		}),
		ReadHeaderTimeout: 10 * time.Second,
	}
	log.Printf("reality-target on %s for %s (classical %v)", *listen, *name, *classical)
	log.Fatal(server.ListenAndServeTLS("", ""))
}
