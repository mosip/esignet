package client

import (
	"compress/gzip"
	"compress/zlib"
	"fmt"
	"io"
	"log/slog"

	http "github.com/aarock1234/fphttp"
	"github.com/andybalholm/brotli"
	"github.com/klauspost/compress/zstd"
)

// decompressResponse replaces resp.Body with a decompressing reader based on
// the Content-Encoding header. After decompression, the Content-Encoding
// header is removed, Content-Length is set to -1, and Uncompressed is set
// to true. If the encoding is empty or unrecognized, resp is unchanged.
func decompressResponse(resp *http.Response) error {
	encoding := resp.Header.Get("Content-Encoding")
	if encoding == "" {
		return nil
	}

	var (
		reader io.ReadCloser
		err    error
	)

	switch encoding {
	case "gzip":
		reader, err = gzip.NewReader(resp.Body)
		if err != nil {
			return fmt.Errorf("creating gzip reader: %w", err)
		}
	case "deflate":
		reader, err = zlib.NewReader(resp.Body)
		if err != nil {
			return fmt.Errorf("creating deflate reader: %w", err)
		}
	case "br":
		reader = io.NopCloser(brotli.NewReader(resp.Body))
	case "zstd":
		dec, err := zstd.NewReader(resp.Body)
		if err != nil {
			return fmt.Errorf("creating zstd reader: %w", err)
		}
		reader = dec.IOReadCloser()
	default:
		slog.Warn("unknown content encoding, skipping decompression", "encoding", encoding)
		return nil
	}

	resp.Body = &decompressBody{
		ReadCloser: reader,
		original:   resp.Body,
	}
	resp.Uncompressed = true

	return nil
}

// decompressBody wraps a decompressing reader and closes both the
// decompressor and the original response body.
type decompressBody struct {
	io.ReadCloser
	original io.ReadCloser
}

func (d *decompressBody) Close() error {
	err := d.ReadCloser.Close()
	if closeErr := d.original.Close(); closeErr != nil && err == nil {
		err = closeErr
	}

	return err
}
