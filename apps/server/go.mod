module github.com/fredrick-karuri/nativestream/server

go 1.26.5

require (
	github.com/coder/websocket v1.8.15
	github.com/fredrick-karuri/nativestream/packages/discovery v0.0.0-00010101000000-000000000000
	github.com/fredrick-karuri/nativestream/packages/mediaplane v0.0.0-00010101000000-000000000000
	github.com/fredrick-karuri/nativestream/packages/proxy v0.0.0-00010101000000-000000000000
	github.com/fredrick-karuri/nativestream/sdk-gen/go v0.0.0-00010101000000-000000000000
	github.com/google/uuid v1.6.0
	github.com/grandcat/zeroconf v1.0.0
	google.golang.org/protobuf v1.36.11
	gopkg.in/yaml.v3 v3.0.1
)

require (
	github.com/cenkalti/backoff v2.2.1+incompatible // indirect
	github.com/miekg/dns v1.1.27 // indirect
	golang.org/x/crypto v0.53.0 // indirect
	golang.org/x/net v0.56.0 // indirect
	golang.org/x/sys v0.46.0 // indirect
)

replace github.com/fredrick-karuri/nativestream/sdk-gen/go => ../../packages/sdk-gen/go

replace github.com/fredrick-karuri/nativestream/packages/mediaplane => ../../packages/mediaplane

replace github.com/fredrick-karuri/nativestream/packages/discovery => ../../packages/discovery

replace github.com/fredrick-karuri/nativestream/packages/proxy => ../../packages/proxy
