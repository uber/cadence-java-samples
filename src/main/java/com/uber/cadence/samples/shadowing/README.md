##Shadowing example:

Shadowing worker uses for detecting workflow non-deterministic error 
prior to the workflow code deployment to prod.

More detail can be found: [design doc](https://github.com/uber/cadence/blob/master/docs/design/workflow-shadowing/2547-workflow-shadowing.md)

1. To run this example, start a 0.21+ cadence server.

2. Run a few HelloActivity workflow to generate workflow records.
```
./gradlew -q execute -PmainClass=com.uber.cadence.samples.hello.HelloActivity
```

3. Run the traffic shadowing
```
./gradlew -q execute -PmainClass=com.uber.cadence.samples.shadowing.ShadowTraffic
```

4. No non-deterministic error is expected in the stdout.

5. Add a non backward compatible change to HelloActivity.
```
for example: add a timer between workflow start and activity schedule

Workflow.sleep(1000);

```

6. Run the traffic shadowing
```
./gradlew -q execute -PmainClass=com.uber.cadence.samples.shadowing.ShadowTraffic
```

7. Non-deterministic error is expected in the stdout.


