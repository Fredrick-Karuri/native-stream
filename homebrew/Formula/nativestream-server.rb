# homebrew/Formula/nativestream-server.rb — NS-320
# Homebrew formula for NativeStream Server.
# Place in a tap repo: github.com/fredrick-karuri/homebrew-nativestream

class NativestreamServer < Formula
  desc "Self-healing live sports stream server for NativeStream Mac"
  homepage "https://github.com/fredrick-karuri/nativestream"
  version "4.0.0"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/fredrick-karuri/nativestream/releases/download/v#{version}/nativestream-server-darwin-arm64.tar.gz"
      sha256 "REPLACE_WITH_ACTUAL_SHA256_ARM64"
    else
      url "https://github.com/fredrick-karuri/nativestream/releases/download/v#{version}/nativestream-server-darwin-amd64.tar.gz"
      sha256 "REPLACE_WITH_ACTUAL_SHA256_AMD64"
    end
  end

  def install
    bin.install "nativestream-server"
  end

  def post_install
    (etc/"nativestream").mkpath
    unless (etc/"nativestream/config.yaml").exist?
      (etc/"nativestream/config.yaml").write config_template
    end
  end

  service do
    run [opt_bin/"nativestream-server"]
    keep_alive true
    log_path var/"log/nativestream.log"
    error_log_path var/"log/nativestream-error.log"
    working_dir var
  end

  def caveats
    <<~EOS
      NativeStream Server installed.

      First-time setup:
        Edit the config: #{etc}/nativestream/config.yaml
        Then start:      brew services start nativestream-server

      Server runs at:  http://127.0.0.1:8888
      Playlist URL:    http://127.0.0.1:8888/playlist.m3u
      EPG URL:         http://127.0.0.1:8888/epg.xml
    EOS
  end

  def config_template
    <<~YAML
      # NativeStream Server config
      # host: 127.0.0.1
      # port: 8888
      # discovery_enabled: false
    YAML
  end

  test do
    port = free_port
    ENV["NATIVESTREAM_PORT"] = port.to_s
    pid = fork { exec bin/"nativestream-server" }
    sleep 1
    assert_match "ok", shell_output("curl -s http://127.0.0.1:#{port}/api/health")
  ensure
    Process.kill("TERM", pid)
  end
end