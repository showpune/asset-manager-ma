# Generate the plan

Analysis the user input, create an modernization plan with filename plan.md document and save it in .modernization folder

1) Using the template in path .modkit/modernization_plan_template.md
2) Read the project information from path .github/appmod-java/appcat/result/extracted_info.json
3) Using the knowledge base from the path .modkit/solution-mapping.json about the mapping of issue IDs and solution IDs to solve the issue.

## Steps

1. Identify the migration goal and fill the goal section
2. From the user input, build the consititution to make the plan and fill the consititution part
3. According to the consitution and input, build the scope of migration
4. If you there is any open issues to make a plan
    1) Add your open issues to the section of clarifcation section
    2) Return all the open issues to user for clarification
    3) After user clarificatio, update the plan  
    4) If user skip the issues, find a solution automaticaly
5. Build a task list for migration
    1) For Task migration from X to Y, you need to find a solution id
    2) For upgrade and deployment task, you needn't the solution id

## Complete Criteria

1) All the open issues are clarified and the plan is updated
2) The migration task list is built
3) DON'T RUN the plan if user don't ask you to run