package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestDownRoleReportUsesTheProcessListingAsRaw(t *testing.T) {
	root := t.TempDir()
	dir := filepath.Join(root, "4123")
	if err := os.Mkdir(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	cmdline := "java\x00-Dproc=org.apache.hadoop.hdfs.server.datanode.DataNode\x00"
	if err := os.WriteFile(filepath.Join(dir, "cmdline"), []byte(cmdline), 0o644); err != nil {
		t.Fatal(err)
	}
	body, err := reportBody(root, []string{"NameNode"})
	if err != nil {
		t.Fatal(err)
	}
	var decoded struct {
		RoleLiveness []roleLiveness `json:"roleLiveness"`
	}
	if err := json.Unmarshal(body, &decoded); err != nil {
		t.Fatal(err)
	}
	if len(decoded.RoleLiveness) != 1 {
		t.Fatalf("roles %#v", decoded.RoleLiveness)
	}
	got := decoded.RoleLiveness[0]
	if got.Role != "NameNode" || got.Up {
		t.Fatalf("liveness %#v", got)
	}
	if got.Raw != "java -Dproc=org.apache.hadoop.hdfs.server.datanode.DataNode" {
		t.Fatalf("raw %q", got.Raw)
	}
}

func TestSecondaryNameNodeIsNotNameNode(t *testing.T) {
	down := collect([]string{"NameNode"}, []string{
		"java org.apache.hadoop.hdfs.server.namenode.SecondaryNameNode",
	})
	if down[0].Up {
		t.Fatal("SecondaryNameNode counted as NameNode")
	}
	up := collect([]string{"NameNode"}, []string{
		"java org.apache.hadoop.hdfs.server.namenode.NameNode",
	})
	if !up[0].Up {
		t.Fatal("NameNode process was not up")
	}
}

func TestReportWithoutRolesOmitsLivenessField(t *testing.T) {
	body, err := reportBody(t.TempDir(), nil)
	if err != nil {
		t.Fatal(err)
	}
	var decoded map[string]json.RawMessage
	if err := json.Unmarshal(body, &decoded); err != nil {
		t.Fatal(err)
	}
	if _, ok := decoded["roleLiveness"]; ok {
		t.Fatalf("body %s", body)
	}
}

func TestPostReportSendsNoUserIdentity(t *testing.T) {
	var header http.Header
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		header = r.Header.Clone()
		if r.URL.Path != "/api/hosts/host-1/role-liveness" {
			t.Errorf("path %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()
	if err := postReport(server.URL, "host-1", []byte("{}")); err != nil {
		t.Fatal(err)
	}
	if header.Get("X-ArchOps-User-Id") != "" || header.Get("Authorization") != "" {
		t.Fatalf("headers %v", header)
	}
}
