// service/service.go — NS-170
// Installs and removes a launchd plist so the server auto-starts on login.

package service

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"text/template"
)

const plistLabel = "com.nativestream.server"

const plistTemplate = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>{{.Label}}</string>
    <key>ProgramArguments</key>
    <array>
        <string>{{.BinaryPath}}</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/tmp/nativestream.log</string>
    <key>StandardErrorPath</key>
    <string>/tmp/nativestream-error.log</string>
</dict>
</plist>
`

func plistPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Library", "LaunchAgents", plistLabel+".plist")
}

func Install(binaryPath string) error {
	if binaryPath == "" {
		var err error
		binaryPath, err = os.Executable()
		if err != nil {
			return fmt.Errorf("could not determine binary path: %w", err)
		}
	}

	data := struct {
		Label      string
		BinaryPath string
	}{plistLabel, binaryPath}

	path := plistPath()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return fmt.Errorf("create LaunchAgents dir: %w", err)
	}

	f, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("create plist: %w", err)
	}
	defer f.Close()

	if err := template.Must(template.New("plist").Parse(plistTemplate)).Execute(f, data); err != nil {
		return fmt.Errorf("write plist: %w", err)
	}

	if err := exec.Command("launchctl", "load", path).Run(); err != nil {
		return fmt.Errorf("launchctl load: %w", err)
	}

	fmt.Printf("✓ Service installed: %s\n", path)
	fmt.Println("  Server will start automatically on next login.")
	fmt.Println("  Logs: /tmp/nativestream.log")
	return nil
}

func Uninstall() error {
	path := plistPath()

	_ = exec.Command("launchctl", "unload", path).Run()

	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("remove plist: %w", err)
	}

	fmt.Println("✓ Service removed.")
	return nil
}