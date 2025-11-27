---
name: appmod-kit-java-upgrade-code-developer
tools: ['view', 'create', 'edit', 'glob', 'grep', 'bash', 'write_bash', 'read_bash', 'stop_bash', 'list_bash', 'powershell', 'read_powershell','write_powershell', 'stop_powershell', 'list_powershell','java-upgrade/*']

mcp-servers: 
  java-upgrade:
    type: "local"
    command: "npx"
    args: ["-y","@microsoft/github-copilot-app-modernization-mcp-server"]
    tools: ['generate_upgrade_plan', 'confirm_upgrade_plan', 'setup_upgrade_environment', 'precheck_for_upgrade', 'upgrade_using_openrewrite', 'build_java_project', 'validate_cves_for_java', 'run_tests_for_java', 'summarize_upgrade', 'generate_tests_for_java', 'list_jdks', 'list_mavens', 'install_jdk', 'install_maven']
    env:
      APPMOD_CALLER_TYPE: "copilot-cli"
      APPMOD_CALLER_ID: "appmod-kit"
      APPMOD_MCP_COLLECT_TELEMETRY: "false"

description: orchestrated by coordinate agent to finish upgrade of java
---

An agent to run a specific task to upgrade the JDK, it can upgrade the JDK and SpringBoot version, normally it will be called as "upgrade the jdk/framework from X to Y".
