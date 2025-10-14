# Execute the plan

1. Copy all the task list of plan.md into the progress.md and loop each task in the progress.md and call the tools to execute the task.

2. You MUST update the status of each task in progress.md if it is finished:
    1) If one task is done without error, update the status as Completed
    2) If one task is done with error, update the status as failed
    3) If one task is canclled, update the status as cancelled

3. call the right tools to finish the migration task one by one and NEVER call two task in parallel

4. Tool usage to complete the task :
    1) call the powershell tool with command to upgrade the java version

    ```shell
    copilot -p {customer upgrade intent} --allow-all-tools --log-dir ./tasklogs > ./tasklogs/upgradelogs.log
    
    ```

    2) call the powershell tool with command to migrate from X to Y with solution id in plan

    ```shell
    copilot -p "Migrate the project to Azure by using tool #appmod-run-task with kbId '{solutionid}' " --allow-all-tools --log-dir ./tasklogs > ./tasklogs/{solutionid}logs.log
    
    ```

5. DON'T Stop before all the tasks is completed or cancelled
