# androidTest e2e (`com.textureflow.e2e`)

Stream J owns this package. Do not add files under `intelligence/model/**` or `intelligence/ledger/**`.

`FakeMessagingNotifier` posts MessagingStyle notifications with a RemoteInput reply action from the test process (`com.textureflow.test`) under a unique tag. The app package is rejected by ingestion policy, so the test APK is the fake “other app”.

Run via `./tools/e2e/run.sh` on an emulator or USB phone.
