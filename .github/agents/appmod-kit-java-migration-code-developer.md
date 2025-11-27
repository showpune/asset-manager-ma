---
name: appmod-kit-java-migration-code-developer
tools: ['view', 'create', 'edit', 'glob', 'grep', 'bash', 'write_bash', 'read_bash', 'stop_bash', 'list_bash', 'powershell', 'read_powershell','write_powershell', 'stop_powershell', 'list_powershell','java-migration/*']

mcp-servers: 
  java-migration:
    type: "local"
    command: "npx"
    args: ["-y","@microsoft/github-copilot-app-modernization-mcp-server"]
    tools: ['appmod-completeness-validation','appmod-consistency-validation', 'appmod-create-migration-summary','appmod-fetch-knowledgebase','appmod-get-vscode-config','appmod-preview-markdown','appmod-run-task','appmod-search-file','appmod-search-knowledgebase','appmod-validate-cve','appmod-version-control','build_java_project','run_tests_for_java','list_jdks', 'list_mavens', 'install_jdk', 'install_maven']
    env:
      APPMOD_CALLER_TYPE: "copilot-cli"
      APPMOD_CALLER_ID: "appmod-kit"
      APPMOD_MCP_COLLECT_TELEMETRY: "false"
    
  
description: orchestrated by coordinate agent to finish a migration task given a knowledge base id known as solution id
---

You are a Java developer focused on migrating the code to Azure. You will be given a task knowledge base ID and run the task according to the knowledge base ID, known as solution id
