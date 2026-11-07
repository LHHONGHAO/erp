// mk-ipk 从已准备好的 staging 目录生成 OpenWrt IPK。
//
// OpenWrt 的 opkg 读取的 IPK 是 gzip 压缩的 tar 容器，内部依次包含
// ./debian-binary、./control.tar.gz、./data.tar.gz（不是 Debian 的 ar 归档）。
// 交叉平台可用（Windows / Linux 均可执行），避免依赖 ar、tar 等外部命令。
package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"
)

func main() {
	stage := flag.String("stage", "", "已准备好的文件根目录")
	out := flag.String("out", "", "输出 ipk 路径")
	pkgName := flag.String("package", "erp-print-agent", "包名")
	version := flag.String("version", "0.1.0", "版本号")
	arch := flag.String("arch", "aarch64_cortex-a53", "目标架构")
	depends := flag.String("depends", "libc", "依赖包")
	conffiles := flag.String("conffiles", "", "配置文件列表，逗号分隔")
	description := flag.String("description", "Remote print agent for jshERP", "包描述")
	maintainer := flag.String("maintainer", "jshERP", "维护者")
	execList := flag.String("exec", "usr/bin/erp-print-agent,etc/init.d/erp-print-agent", "需要 0755 权限的文件，逗号分隔")
	privateList := flag.String("private", "etc/erp-print-agent/config.json", "需要 0600 权限的文件，逗号分隔")
	list := flag.String("list", "", "仅列出指定 ipk 的内容后退出")
	flag.Parse()

	if *list != "" {
		if err := listIPK(*list); err != nil {
			fmt.Fprintf(os.Stderr, "错误: %v\n", err)
			os.Exit(1)
		}
		return
	}

	if *stage == "" || *out == "" {
		fmt.Fprintln(os.Stderr, "用法: mk-ipk -stage <dir> -out <file.ipk> [-version 0.1.0] [-arch ...]")
		os.Exit(2)
	}

	modes := make(map[string]os.FileMode)
	for _, p := range splitList(*execList) {
		modes[p] = 0o755
	}
	for _, p := range splitList(*privateList) {
		modes[p] = 0o600
	}

	if err := build(*stage, *out, *pkgName, *version, *arch, *depends,
		splitList(*conffiles), *description, *maintainer, modes); err != nil {
		fmt.Fprintf(os.Stderr, "错误: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("已生成 %s\n", *out)
}

func splitList(s string) []string {
	var out []string
	for _, part := range strings.Split(s, ",") {
		if p := strings.TrimSpace(part); p != "" {
			out = append(out, strings.TrimPrefix(filepath.ToSlash(p), "./"))
		}
	}
	return out
}

func build(stage, out, pkgName, version, arch, depends string,
	conffiles []string, description, maintainer string, modes map[string]os.FileMode) error {

	info, err := os.Stat(stage)
	if err != nil || !info.IsDir() {
		return fmt.Errorf("staging 目录不可用: %s", stage)
	}

	dataTar, installedKB, err := packData(stage, modes)
	if err != nil {
		return err
	}

	var control bytes.Buffer
	control.WriteString(fmt.Sprintf("Package: %s\n", pkgName))
	control.WriteString(fmt.Sprintf("Version: %s\n", version))
	if depends != "" {
		control.WriteString(fmt.Sprintf("Depends: %s\n", depends))
	}
	control.WriteString("Section: utils\n")
	control.WriteString("Priority: optional\n")
	control.WriteString(fmt.Sprintf("Architecture: %s\n", arch))
	control.WriteString(fmt.Sprintf("Installed-Size: %d\n", installedKB))
	control.WriteString(fmt.Sprintf("Maintainer: %s\n", maintainer))
	control.WriteString(fmt.Sprintf("Description: %s\n", description))

	controlTar, err := packControl(control.Bytes(), conffiles)
	if err != nil {
		return err
	}

	f, err := os.Create(out)
	if err != nil {
		return err
	}
	defer f.Close()

	return packOuter(f, []tarEntry{
		{name: "./debian-binary", data: []byte("2.0\n")},
		{name: "./control.tar.gz", data: controlTar},
		{name: "./data.tar.gz", data: dataTar},
	})
}

func packData(stage string, modes map[string]os.FileMode) ([]byte, int64, error) {
	var buf bytes.Buffer
	gz := gzip.NewWriter(&buf)
	gz.Header.ModTime = time.Unix(0, 0)
	tw := tar.NewWriter(gz)

	var installedSize int64
	err := filepath.WalkDir(stage, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		rel, err := filepath.Rel(stage, path)
		if err != nil {
			return err
		}
		if rel == "." {
			return nil
		}
		rel = filepath.ToSlash(rel)

		hdr := &tar.Header{
			Name:    "./" + rel,
			Uid:     0,
			Gid:     0,
			Uname:   "root",
			Gname:   "root",
			ModTime: time.Unix(0, 0),
		}
		if d.IsDir() {
			hdr.Typeflag = tar.TypeDir
			hdr.Name += "/"
			hdr.Mode = 0o755
		} else {
			info, err := d.Info()
			if err != nil {
				return err
			}
			if !info.Mode().IsRegular() {
				return nil
			}
			hdr.Typeflag = tar.TypeReg
			hdr.Size = info.Size()
			hdr.Mode = 0o644
			if m, ok := modes[rel]; ok {
				hdr.Mode = int64(m)
			}
			installedSize += info.Size()
		}
		if err := tw.WriteHeader(hdr); err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		_, err = tw.Write(data)
		return err
	})
	if err != nil {
		return nil, 0, err
	}
	if err := tw.Close(); err != nil {
		return nil, 0, err
	}
	if err := gz.Close(); err != nil {
		return nil, 0, err
	}
	return buf.Bytes(), (installedSize + 1023) / 1024, nil
}

func packControl(control []byte, conffiles []string) ([]byte, error) {
	var buf bytes.Buffer
	gz := gzip.NewWriter(&buf)
	gz.Header.ModTime = time.Unix(0, 0)
	tw := tar.NewWriter(gz)

	if err := writeTarFile(tw, "./control", control, 0o644); err != nil {
		return nil, err
	}
	if len(conffiles) > 0 {
		var cf bytes.Buffer
		for _, p := range conffiles {
			cf.WriteString(p)
			cf.WriteByte('\n')
		}
		if err := writeTarFile(tw, "./conffiles", cf.Bytes(), 0o644); err != nil {
			return nil, err
		}
	}
	if err := tw.Close(); err != nil {
		return nil, err
	}
	if err := gz.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func writeTarFile(tw *tar.Writer, name string, data []byte, mode int64) error {
	hdr := &tar.Header{
		Name:     name,
		Typeflag: tar.TypeReg,
		Mode:     mode,
		Size:     int64(len(data)),
		Uid:      0,
		Gid:      0,
		Uname:    "root",
		Gname:    "root",
		ModTime:  time.Unix(0, 0),
	}
	if err := tw.WriteHeader(hdr); err != nil {
		return err
	}
	_, err := tw.Write(data)
	return err
}

type tarEntry struct {
	name string
	data []byte
}

// packOuter 把 debian-binary、control.tar.gz、data.tar.gz 打包为
// gzip 压缩的 tar 容器，这是 OpenWrt opkg 期望的 IPK 格式。
func packOuter(w io.Writer, entries []tarEntry) error {
	gz := gzip.NewWriter(w)
	gz.Header.ModTime = time.Unix(0, 0)
	tw := tar.NewWriter(gz)
	for _, e := range entries {
		if err := writeTarFile(tw, e.name, e.data, 0o644); err != nil {
			return err
		}
	}
	if err := tw.Close(); err != nil {
		return err
	}
	return gz.Close()
}

// listIPK 打印 ipk 内的成员与 tar 条目，用于构建后自检。
func listIPK(path string) error {
	raw, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	if bytes.HasPrefix(raw, []byte("!<arch>\n")) {
		return errors.New("这是 ar 格式的旧包，OpenWrt opkg 无法安装")
	}
	gz, err := gzip.NewReader(bytes.NewReader(raw))
	if err != nil {
		return fmt.Errorf("不是 gzip 压缩的 tar 容器: %w", err)
	}
	defer gz.Close()
	tr := tar.NewReader(gz)
	for {
		hdr, err := tr.Next()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}
		fmt.Printf("%-6o %10d  %s\n", hdr.Mode, hdr.Size, hdr.Name)
		if !strings.HasSuffix(hdr.Name, ".tar.gz") {
			continue
		}
		data, err := io.ReadAll(tr)
		if err != nil {
			return err
		}
		if err := listTarGz(data); err != nil {
			return err
		}
	}
}

func listTarGz(raw []byte) error {
	gz, err := gzip.NewReader(bytes.NewReader(raw))
	if err != nil {
		return err
	}
	defer gz.Close()
	tr := tar.NewReader(gz)
	for {
		hdr, err := tr.Next()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}
		fmt.Printf("  %-6o %10d  %s\n", hdr.Mode, hdr.Size, hdr.Name)
	}
}
