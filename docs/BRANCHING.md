# Branching Strategy & Deployment Triggers

[img_strategy]: diagrams/branches_and_deployment.png

The diagram below provides view of:
* How various branches can be used for enhancements/maintenances/fixes to the repository
* How deployment to various environments gets triggered automatically from various branches

![Branching_Deployment_strategy][img_strategy]

In General:
* `develop` branch - (under develop) should represent most up-to-date (may be unstable) version in **DEV** environment
* `int` branch - (intergration) should represent most stable version in **E2E** environment
* `master` branch - (main) should represent most stable version in **Prod** environment
* `hot_xxx` branch - (hot fixes) is the branch, can be forked from any of the above branches, to make and merge a quick fix
