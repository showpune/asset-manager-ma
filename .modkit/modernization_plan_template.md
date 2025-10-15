# Modernization Plan

## Summary

Summary the customer input here

## Modernization Goal

Describe the customer modernization goal of the application here, e.g.,Migrate the project to Azure Container Apps (from user input)
Ensure that the application's modernization goal is unambiguous; for instance, the target service must be unique.

## Scope

This part described the scope that the modernizatin plan can cover

According to the provided project information, summary the scope for modernization, the scope should be only related with code change and the scope type will be one of belows

1. Upgrade
2. Migration Code Change the project from using x to using Y, x is a on-premise resource and Y is an Azure Hosting resource
3. CodetoCloud

The scope is described as below format as a sample:

1. Java Upgrade
   - JDK Upgrade
   - Spring Framework Upgrade
2. Migration Code Change
   - Migrate from X1 to Y2
   - Using Azure Keyvalut to manage the secret

## Plan Genration Rules

### Builtin Best Practices

It is some built-in best practics to migrate to azure, which is used to generate the plan, it needn't be included in the generated plan.md document

1. Upgrade the application first, then do the migration from X to Y, then Container the app and generate deployment files
2. Only run build after the all the upgrade and migration tasks are done

### Global Constitution

If customer provided the global consistution for modernization, summarize the global consitution below as a list, the below is a sample:

1) The JDK must be upgraded to JDK 17
2) Using Postgresql for database migration

### Roundtrip Constitution

Summarize the roundtrip consitution for modernization below as a list, it is summarized from customer input, and it is some customization of rule just for this roundtrip of plan

## Application Information

### Current Architecture

According to user input, describe the application architecture with diagram of mermaid, including:

1) Application framework information
2) Resource/Services dependencies
3) Connector framework to the depedencies

## Clarification

Before you make a target architecture and Task Breakdown for next sections, list all the open issues you need to clarfy with user as a list with format as below:

1) Open issue 1: [Describe the issue here]  
   - Answer: [User Answer]  
   - Status: Solved

All the open issues should be related with task breakdown for code change, NOT list the issue with infra.

The open issues is possible be:

1) You don't have enough information to clarify if this issue is a really issue
2) The are multiple solutions for the same issue and the solutions are conflict, and you don't have enough information to decide solution you need to choose

This section is not mandatory, just leave if empty if no open issues

## Migration Plan

### Target Architecture

Define the target architecture for modernization with diagram of mermaid, according the the solution knowledge base that Azure suppport including

1) Application framework information
2) Target Resource/Services dependencies
3) Connector framework to the depedencies

### Task Breakdown and Dependencies

The task status must be update if one task is executed, the below is the sample tasks, the task type is a scope type:

1) Task name: Upgrade JDK to 17  
   - Task Type: Java Upgrade  
   - Issues Detail: [The issue to be solve by the task]  
   - Solution Id: N/A  
   - Depends on Task: NA  
   - Status: Completed
2) Task name: Migration from X to Y  
   - Task Type: Migration Code Change  
   - Issues Detail: [The issue to be solve by the task]  
   - Solution Id: id1  
   - Depends on Task: Upgrade JDK to 17  
   - Status: Not started  
