# Modernization Planning Prompt Template: [Technology X] to [Technology Y]

## Modernization Planning Request

Create a detailed modernization plan for transforming this codebase from [Technology X] to [Technology Y], focusing on identifying the required changes, their sequence, dependencies, and associated risks.

## Scope

- ✅ Analysis of required code modifications to move from [X] to [Y]
- ✅ Identification of configuration changes needed 
- ✅ Dependency management planning (pom.xml, package.json, etc.)
- ✅ Visual modernization sequence diagrams with Mermaid (X -> Y paths)
- ✅ Risk assessment and mitigation strategies
- ✅ Technical roadmap with clear implementation phases
- ✅ Modernization success criteria and validation approach

## Success Criteria

1. Comprehensive modernization plan with clear implementation phases
2. Detailed X -> Y modernization sequence with dependencies and ordering
3. Visual diagrams illustrating the modernization path 
4. Risk assessment with mitigation strategies for each phase
5. Success metrics and validation approach for the modernization

## Execution Process

1. Analyze the codebase to identify all [Technology X] components and usages
2. Create a `plan.md` file documenting:
   - Current state assessment
   - Target architecture vision
   - Modernization phases and tasks
   - Risk assessment and mitigation strategies
   - Resource requirements and timeline estimates
3. Create a `migration-sequence-diagrams.md` file with:
   - Complete modernization sequence with dependencies
   - Specific X -> Y modernization paths for each technology component
   - Decision trees for key modernization choices
   - Critical path analysis
4. Define modernization phases systematically:
   - Foundation setup and preparation
   - Core technology upgrades
   - Framework and library modernization
   - Architecture improvements
   - Quality and security enhancements
5. For each phase, provide:
   - Detailed tasks and their dependencies
   - Technical implementation guidance
   - Validation and verification approach
   - Risk assessment and mitigation

## Modernization Planning Documents

Create a `plan.md` file with:
- [ ] Current state assessment
- [ ] Target architecture vision
- [ ] Modernization phases and tasks with clear descriptions
- [ ] Dependencies between tasks and phases
- [ ] Risk assessment and mitigation strategies
- [ ] Resource requirements and timeline estimates
- [ ] Success metrics and validation approach

Create a `migration-sequence-diagrams.md` file with:
- [ ] X -> Y modernization steps table showing the precise ordered sequence
- [ ] Complete modernization sequence with all steps using Mermaid diagrams, make sure all the steps is the same with steps table
- [ ] Specific X -> Y modernization paths for each technology
- [ ] Dependencies between different components
- [ ] Critical path analysis
- [ ] Risk assessment visualization

## Modernization Sequencing

Create a detailed X -> Y modernization steps table showing the precise ordered sequence:

| Order | From (X) | To (Y) | Dependencies | Migration Type | Risk Level | Description |
|-------|----------|--------|--------------|------------|------------|-------------|
| 1 | [Example X] | [Example Y] | None | Application Code Change |Low | First modernization step |
| 2 | [Example X] | [Example Y] | Step 1 | CICD Change |Medium | Second modernization step |
| 2 | [Example X] | [Example Y] | Step 2 | Mannul Check |Medium | Second modernization step |

Create clear modernization sequences with dependencies visualized through Mermaid diagrams:
```mermaid
flowchart TB
    %% Example modernization sequence
    start["Current State: [X]"] --> step1["Intermediate Step"] --> end["Target State: [Y]"]
```

Each X -> Y modernization path should be documented with:
1. Prerequisites and dependencies
2. Step-by-step implementation approach
3. Validation and verification methods
4. Risks and mitigation strategies
5. Common challenges and solutions

## Technical Implementation Guidance

For each modernization phase, provide detailed technical implementation guidance:
```
# Phase: [Phase Name]
# Task: [Task Description]
# Current Implementation: [Description or code snippet]
# Target Implementation: [Description or code snippet]
# Implementation Steps: 
1. [Step 1]
2. [Step 2]
# Verification: [How to verify success]
```

Ensure the modernization plan is comprehensive, addressing all aspects of the transformation from [Technology X] to [Technology Y].
