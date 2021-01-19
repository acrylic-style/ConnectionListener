# ConnectionListener
A plugin to allow creating multiple listeners (listening ports)

## What is this, and what this plugin does?
- Provides a feature to create multiple connection listener
- Able to listen connections on multiple ports
- Comes with HAProxy support

Configuration file
----
```yaml
# Welcome to ConnectionListener configuration file!
# There are some things to configure, and I'll explain what are these values and that does that do.
# 
# These settings will affect whether if ConnectionListener will try to create an extra listener.
# If proxy_protocol is disabled, then ConnectionListener will do completely nothing about HAProxy.
# Also, enabling proxy_protocol might causes ViaVersion to stop working after /reload.
# Please restart the server if you see the error from ViaVersion in console.
# 
# Important notes:
#  - You cannot load plugin (by PlugMan or something) AFTER the server is fully started.
#  - Breaks ViaVersion when you do /reload
# 
# ===== Connection Listeners ===== ('listeners' list)
# 
# proxy_protocol:
#     Sets whether enables PROXY protocol for usage from HAProxy etc.
#     **HAProxy features will be disabled if it is set to false.**    (Default: false)
# server_ip:
#     Set specific server IP / Hostname when you have multiple NICs.
#     (Requires proxy_protocol to work.)
#     (Default: 'null')
# port:
#     Sets port to listen HAProxy.
#     Specify -1 to replace main server port with HAProxy-enabled listener.
#     If you specify the port other than -1, then the HAProxy-enabled packet
#     listener will be created.
#     (Requires proxy_protocol to work.)
#     (Default: -1)
# epoll:
#     Whether to enable epoll on linux servers.
#     Epoll enables native enhancements to listener, and it is recommend to enable it.
#     Please note that epoll is not available on Windows etc, so epoll will not be used
#     and the Netty IO will be used.
#     (Requires proxy_protocol to work.)
#     (Default: true)
# 
# ===== EXPERIMENTAL =====
# These settings are experimental and might not work, or may behave weird!
# Use at your own risk.
# 
# experimental.useReflectionChannelInitializer:
#     Whether to use reflection-based channel initializer when creating HAProxy-enabled
#     packet listener.
#     This setting has no effect when replacing main server port with HAProxy-enabled listener.
#     (Requires proxy_protocol to work.)
#     (Default: false)

listeners:
  - proxy_protocol: true
    epoll: true
    port: -1 # inject haproxy support for port defined on server.properties
    server_ip: 'null'
    experimental:
      useReflectionChannelInitializer: true
  - proxy_protocol: true
    epoll: true
    port: 25566
    server_ip: 'null'
    experimental:
      useReflectionChannelInitializer: true
  - proxy_protocol: false
    epoll: true
    port: 25567
    server_ip: 'null'
    experimental:
      useReflectionChannelInitializer: false

```
