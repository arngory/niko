package raysub

import (
	"context"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"math/rand/v2"
	"net"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// app-update-v1 (raycontrol docs/spec/app-update-v1.md): the app's releases a node holds, their files at
// /app/v1/<app>/<tag>/<name> on the subscription port, behind the same TLS, SPA v2 and RC1 as /sub/v1. Nothing that
// comes from there is trusted: the app checks the release's signature and each file's hash.

var (
	appTag  = regexp.MustCompile(`^v[0-9A-Za-z][0-9A-Za-z.+-]{0,63}$`)
	appName = regexp.MustCompile(`^[A-Za-z0-9._-]{1,128}$`)

	// ErrAppNotHeld: the node does not hold this file (its mirror off or not filled yet, the release gone, or the
	// device unknown to it: 404, as /sub/v1's).
	ErrAppNotHeld = errors.New("raycontrol app mirror: the node does not hold this file")
	// ErrAppTooLarge: more came than such a file holds (the node is trusted for nothing): what came is dropped
	ErrAppTooLarge = errors.New("raycontrol app mirror: the file is larger than a release's file can be")

	errAppRange = errors.New("raycontrol app mirror: the part kept is not of this file")
)

const (
	// a transfer goes on while it moves (a DNS tunnel's few KB/s) and ends when nothing comes for this long
	appIdle = 2 * time.Minute
	// 429: the device's one download at a time, or the node's sixteen; waited for, three tries in all
	appTries   = 3
	appMaxWait = 60 * time.Second
)

type busyError time.Duration

func (e busyError) Error() string { return "HTTP 429" }

// AppFile writes the file name of the app's release tag, as the node of r holds it, to out. With etag (the file's
// sha256 in quotes, from the release's signed SHA256SUMS) and a part of the file in out already, it asks for the rest
// only (Range, If-Range: a node that holds another file sends it whole, and out starts over). The knock and the
// request go as Fetch's: UDP, then the TCP carriers; a transfer that breaks goes on from what it has with the next.
func AppFile(ctx context.Context, r Request, app, tag, name, out, etag string) error {
	if app != "niko" || !appTag.MatchString(tag) || !appName.MatchString(name) || strings.Trim(name, ".") == "" {
		return fmt.Errorf("raycontrol app mirror: not a file of a release: %q %q %q", app, tag, name)
	}
	now := time.Now
	if r.Now != nil {
		now = r.Now
	}
	deviceKey, err := decodeDeviceKey(r.SPA.DeviceKey)
	if err != nil {
		return err
	}
	// no bound for the whole: the idle bound in appGet
	client := r.httpClient(0)
	defer client.CloseIdleConnections()
	url := "https://" + net.JoinHostPort(r.Host, strconv.Itoa(r.Port)) + "/app/v1/" + app + "/" + tag + "/" + name
	carriers := r.SPA.FetchCarriers()
	var lastErr error
	busy, restarted := 0, false
	for i := 0; i < len(carriers); {
		if err := r.knock(ctx, carriers[i], now()); err != nil {
			return err
		}
		before := fileSize(out)
		err := r.appGet(ctx, client, url, deviceKey, now(), out, etag)
		if err == nil {
			return nil
		}
		lastErr = err
		var wait busyError
		var status statusError
		switch {
		case errors.As(err, &wait):
			busy++
			if busy >= appTries {
				return fmt.Errorf("raycontrol app mirror: %w", err)
			}
			select {
			case <-ctx.Done():
				return ctx.Err()
			// the node's Retry-After plus up to half of it again: the devices it turned away come back spread (neko)
			case <-time.After(time.Duration(wait) + time.Duration(rand.Int64N(int64(wait)/2+1))):
			}
			continue
		case errors.Is(err, errAppRange) && !restarted:
			// out was cut to nothing: the whole, once, the same way
			restarted = true
			continue
		case errors.Is(err, ErrAppNotHeld), errors.Is(err, ErrAppTooLarge), errors.Is(err, errPin), errors.As(err, &status), ctx.Err() != nil:
			// the node answered (or the caller gave up): another knock changes nothing
			return err
		case etag != "" && fileSize(out) > before:
			// cut short after some of it came (the node ends a download at 15 min, or one that stalls a minute): the
			// rest the same way, as long as each try brings more; ctx bounds the whole
			continue
		}
		i++
	}
	return fmt.Errorf("%w: %v", ErrNoAnswer, lastErr)
}

// appMaxSize is the most a release's file may hold: the signed list and its signature are small, an APK is not
// (neko: a node is trusted for nothing, and the list is read whole into memory after).
func appMaxSize(url string) int64 {
	if strings.HasSuffix(url, ".apk") {
		return 256 << 20
	}
	return 64 << 10
}

// fileSize is the size of the file at path, 0 when there is none.
func fileSize(path string) int64 {
	info, err := os.Stat(path)
	if err != nil {
		return 0
	}
	return info.Size()
}

func decodeDeviceKey(text string) ([]byte, error) {
	key, err := hex.DecodeString(text)
	if err != nil || len(key) != 32 {
		return nil, errors.New("raycontrol subscription: bad device key")
	}
	return key, nil
}

// appGet asks for the file once, from the end of what out holds when etag is known, and writes what comes.
func (r *Request) appGet(ctx context.Context, client *http.Client, url string, deviceKey []byte, now time.Time,
	out, etag string) error {
	f, err := os.OpenFile(out, os.O_RDWR|os.O_CREATE, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()
	have, err := f.Seek(0, io.SeekEnd)
	if err != nil {
		return err
	}
	if etag == "" && have > 0 {
		// nothing to tell this file's part from another's: the whole
		if err = f.Truncate(0); err != nil {
			return err
		}
		have = 0
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	request.Header.Set("X-Raycontrol-Device", r.DeviceID)
	request.Header.Set("Authorization", authToken(deviceKey, now.Unix()/60))
	if have > 0 {
		request.Header.Set("Range", "bytes="+strconv.FormatInt(have, 10)+"-")
		request.Header.Set("If-Range", etag)
	}
	// the dial, the handshake and the answer's start count as a wait without progress
	idle := time.AfterFunc(appIdle, cancel)
	defer idle.Stop()
	response, err := client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	switch response.StatusCode {
	case http.StatusOK:
		// the whole: out starts over
		if err = f.Truncate(0); err != nil {
			return err
		}
		if _, err = f.Seek(0, io.SeekStart); err != nil {
			return err
		}
		have = 0
	case http.StatusPartialContent:
		if start, ok := rangeStart(response.Header.Get("Content-Range")); !ok || start != have {
			return fmt.Errorf("raycontrol app mirror: the rest asked from %d, %q came", have, response.Header.Get("Content-Range"))
		}
	case http.StatusRequestedRangeNotSatisfiable:
		// out holds the whole or more of another file: from the start
		if err = f.Truncate(0); err != nil {
			return err
		}
		return errAppRange
	case http.StatusTooManyRequests:
		return busyError(retryAfter(response.Header.Get("Retry-After")))
	case http.StatusNotFound:
		return ErrAppNotHeld
	default:
		return statusError(response.StatusCode)
	}
	buffer := make([]byte, 64<<10)
	var got int64
	for {
		n, err := response.Body.Read(buffer)
		if n > 0 {
			idle.Reset(appIdle)
			if _, err := f.Write(buffer[:n]); err != nil {
				return err
			}
			got += int64(n)
			if have+got > appMaxSize(url) {
				_ = f.Truncate(0)
				return ErrAppTooLarge
			}
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}
	}
	if response.ContentLength >= 0 && got != response.ContentLength {
		return fmt.Errorf("raycontrol app mirror: %d of %d bytes", got, response.ContentLength)
	}
	return nil
}

// rangeStart is the first byte of a Content-Range "bytes a-b/n".
func rangeStart(header string) (int64, bool) {
	spec, ok := strings.CutPrefix(header, "bytes ")
	if !ok {
		return 0, false
	}
	first, _, ok := strings.Cut(spec, "-")
	if !ok {
		return 0, false
	}
	start, err := strconv.ParseInt(first, 10, 64)
	return start, err == nil && start >= 0
}

// retryAfter is Retry-After in seconds, 5 s without one, a minute at most.
func retryAfter(header string) time.Duration {
	seconds, err := strconv.Atoi(strings.TrimSpace(header))
	if err != nil || seconds < 1 {
		return 5 * time.Second
	}
	return min(time.Duration(seconds)*time.Second, appMaxWait)
}
