# Execute the plan

1. Copy all the task list in .modernization/plan.md into the modernization/progress.md and loop each task in the progress.md and call the tools to execute the task.

2. You MUST update the status of each task in progress.md if it is finished:
    1) If one task is done without error, update the status as Completed
    2) If one task is done with error, update the status as failed
    3) If one task is canclled, update the status as cancelled

3. call the right agent to finish the migration task one by one and NEVER call two task in parallel

4. Agent usage to complete the task :
    1) Agent javaupgrade-code-developer to upgrade the java version
    2) Agent migration-code-developer for migrate from X to Y

5. DON'T Stop before all the tasks is completed or cancelled