# plugin-navs

Halo 2.0 的导航管理插件，halo-theme-webstack依赖它。

## 开发环境

```bash
git clone git@github.com:zuoer96/halo-plugin-navs.git

# 或者当你 fork 之后

git clone git@github.com:{your_github_id}/halo-plugin-navs.git
```

```bash
cd path/to/halo-plugin-navs
```

```bash
# macOS / Linux
./gradlew pnpmInstall

# Windows
./gradlew.bat pnpmInstall
```

```bash
# macOS / Linux
./gradlew build

# Windows
./gradlew.bat build
```

修改 Halo 配置文件：

```yaml
halo:
  plugin:
    runtime-mode: development
    fixedPluginPath:
      - "/path/to/halo-plugin-navs"
```

