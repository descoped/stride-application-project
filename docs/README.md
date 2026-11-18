# Stride developer documentation

How to build an application on Stride. Each document covers one module: what it is for, how to use it, and a
reference of its configuration keys and classes.

Start with the [README](../README.md) for a first application, then read what you need.

| Document | Module | Read it to |
|---|---|---|
| [The runtime](core.md) | `stride-application-core` | Add services, REST resources, servlets and filters; understand run levels, threads and shutdown. |
| [Lifecycle and components](api.md) | `stride-application-api` | Create, start and stop an application, and hand Stride the objects you built yourself. |
| [Configuration](config.md) | `stride-application-config` | Declare and read keys, override them from the environment, and declare components in code. |
| [Testing](testing.md) | `stride-application-junit-jupiter` | Test an application with a real server and real requests. |
| [Authentication](security.md) | `stride-application-security` | Optional. Protect an application with JWT bearer tokens. |
| [OpenAPI](openapi.md) | `stride-application-openapi` | Optional. Serve the OpenAPI description of your resources. |
| [The sample application](sample.md) | `stride-application-sample` | See a complete, tested application to copy from. |

What changed between versions is in the [changelog](../CHANGELOG.md).
