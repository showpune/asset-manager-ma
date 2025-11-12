---
description: Create a modernization plan that describes the tasks that need to be done
---

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).

## Outline

The text the user typed after `/appmod-kit.create-plan` in the triggering message **is** the modernization description. Assume you always have it available in this conversation even if `$ARGUMENTS` appears literally below. Do not ask the user to repeat it unless they provided an empty command. If the user provided an empty command, you **must** ask them to provide a modernization description.

**ALWAYS** use the tool from Github MCP server to access the Github.

Given that modernization description, do this:

1. **Generate a concise short name** (5-10 words) for the branch:
   - Analyze the modernization description and extract the most meaningful keywords
   - Create a 5-10 word short name that captures the essence of the modernization intent
   - Use action-noun or action-old-"to"-new format when possible (e.g., "add-managed-identity", "upgrade-jdk11-to-jdk17")
   - Preserve technical terms and acronyms (OAuth2, API, JWT, etc.)
   - Keep it concise but descriptive enough to understand the feature at a glance
   - Ignore any URIs
   - Examples:
     - "I want to add managed identity to my SQL Server" → "add-managed-identity"
     - "Replace the use of Rabbit MQ with Azure Service Bus" → "migrate-rabbit-mq-to-azure-service-bus"
     - "Modernize my application" → "modernize-complete-application"

2. **Extract GitHub issue URI**:
   - Extract the GitHub issue URI from the arguments if you can
   - Validate the URI format if it exists (you can assume that syntax validity is enough)

3. Run `python .appmod-kit/scripts/python/create_plan.py --json` with args short-name and github-issue-uri from the repo root and parse its JSON output.
   **IMPORTANT**:
   - Append the short-name argument `--short-name "your-generated-short-name"` to the `python .appmod-kit/scripts/python/create_plan.py --json` command with the 5-10 word short name you created in step 1.
   - Append the GitHub issue URI argument `--github-issue-uri "extracted-github-issue-uri"` to the `python .appmod-kit/scripts/python/create_plan.py --json` command if you extracted it in step 2.
   - For single quotes in arguments like "I'm Groot", use escape syntax: e.g 'I'\''m Groot' (or double-quote if possible: "I'm Groot")
   - You must only ever run this script once
   - The JSON is provided in the terminal as output - always refer to it to get the actual content you're looking for

4. Create a modernization plan
    1) Read the file ".appmod-kit/templates/plan-template.md" from the project path to get a plan template for generating a modernization plan.
    2) Save it in the plan folder created by `python .appmod-kit/scripts/python/create_plan.py --json` with the filename plan.md. If a plan already exists, overwrite it.
    3) Read the solution information using MCP tool **appmod-list-all-solutions** to access the knowledge base and learn about the mapping of issue rule IDs to solution IDs in order to solve issues.
    4) **appmod-list-all-solutions NOT FOUND** Stop to create plan if MCP tool **appmod-list-all-solutions** is not found and ask user to install the MCP server for migration
    
    4) If user only want to upgrade or deploy the application without migration, skip step 3).

## Build Modernization Plan Steps

1. **Load context**: Retrieve the files or github issues user mentioned in the input.
    1) Get the project information from input file/github issues, respect the project information from input file/github issue, if provided, no need to scan codebase.
    2) Get the knowledge base for modernization
    3) Collect the plan generation rules from input

2. **Generate plan**: Generate plan.md using plan-template.md, you will read
    1) follow the structure of the plan-template.md
    2) follow the rules defined in the template to fill in the sections with relevant information based on the analysis of user input and content of mentioned files

3. **Rule to Pickup solution**
    1) Always repect user intent, pickup solutions according to user input, like "Migrate from X to Y" or "Upgrade Java/Framework from X to Y".
    2) Rules to Upgrade Java - **CRITICAL: Avoid Creating Duplicate Tasks**
      - The latest version of Java is above 17, the latest of Spring Boot is above 3 and the latest Spring Framework is above 6
      - **Choose the right upgrade solution id set and avoid duplicate work in solution. If solution A contains solution B, only pick solution A.**
        - **If Spring Boot is asked to upgrade to 3.x or above without explicitly requesting Java 21 or above:**
            - **DO NOT create separate tasks** for: JDK upgrade, Spring Framework upgrade, or Jakarta EE upgrade
            - **ONLY create ONE task: "Upgrade Spring Boot to 3.x"**
            - In the task description, explicitly state that it includes: upgrading JDK to 17, Spring Framework to 6.x, and migrating from JavaEE (javax.*) to Jakarta EE (jakarta.*)
        - **If Spring Framework is asked to upgrade to 6.x or above (without Spring Boot upgrade) without explicitly requesting Java 21 or above:**
            - **DO NOT create a separate task** for: JDK upgrade
            - **ONLY create ONE task: "Upgrade Spring Framework to 6.x"**
            - In the task description, explicitly state that it includes: upgrading JDK to 17
        - **If JDK upgrade is explicitly requested with version 21 or above:**
            - Create a task: "Upgrade Java to version X"

4. **Clarification**: If there are any open issues in the plan
    1) Return all the open issues to user for clarification
    2) After user clarified, update the plan
    3) If user skip the issues, find a solution automatically

5. Make a git commit with message "chore: create modernization plan --short-name" to save the plan.md

6. Using the 'github-mcp-server' tool, add a new comment to the GitHub issue URI (GITHUB_ISSUE_URI) with the plan summary. In the summary, state "Plan creation succeeded" and give a summary of the plan, specifically, the number of tasks and a summary of each task.


## Completion Criteria

1. All the open issues are clarified and the plan is updated
2. The modernization task list is built
3. The modernization task list MUST be scoped according to user input
4. DON'T RUN the plan if user does not explicitly ask you to run the plan
