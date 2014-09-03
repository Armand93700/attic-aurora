/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aurora.scheduler.app.local;

import java.io.File;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Module;
import com.twitter.common.application.AppLauncher;

import org.apache.aurora.codec.ThriftBinaryCodec.CodingException;
import org.apache.aurora.gen.storage.Snapshot;
import org.apache.aurora.scheduler.DriverFactory;
import org.apache.aurora.scheduler.app.SchedulerMain;
import org.apache.aurora.scheduler.app.local.simulator.ClusterSimulatorModule;
import org.apache.aurora.scheduler.storage.DistributedSnapshotStore;
import org.apache.aurora.scheduler.storage.Storage;
import org.apache.aurora.scheduler.storage.Storage.NonVolatileStorage;
import org.apache.aurora.scheduler.storage.log.LogStorage;
import org.apache.mesos.SchedulerDriver;

/**
 * A main class that runs the scheduler in local mode, using fakes for external components.
 */
public class LocalSchedulerMain extends SchedulerMain {

  @Override
  protected Module getPersistentStorageModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(Storage.class).to(Key.get(Storage.class, LogStorage.WriteBehind.class));
        bind(NonVolatileStorage.class).to(FakeNonVolatileStorage.class);
        bind(DistributedSnapshotStore.class).toInstance(new DistributedSnapshotStore() {
          @Override
          public void persist(Snapshot snapshot) throws CodingException {
            // No-op.
          }
        });
      }
    };
  }

  @Override
  protected Module getMesosModules() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(DriverFactory.class).to(FakeDriverFactory.class);
        bind(SchedulerDriver.class).to(FakeMaster.class);
        bind(FakeMaster.class).in(Singleton.class);
        install(new ClusterSimulatorModule());
      }
    };
  }

  static class FakeDriverFactory implements DriverFactory {
    private final SchedulerDriver driver;

    @Inject
    FakeDriverFactory(SchedulerDriver driver) {
      this.driver = driver;
    }

    @Override
    public SchedulerDriver apply(@Nullable String input) {
      return driver;
    }
  }

  public static void main(String[] args) {
    File backupDir = Files.createTempDir();
    backupDir.deleteOnExit();

    List<String> arguments = ImmutableList.<String>builder()
        .add(args)
        .add("-cluster_name=local")
        .add("-serverset_path=/aurora/local/scheduler")
        .add("-zk_endpoints=localhost:2181")
        .add("-zk_in_proc=true")
        .add("-backup_dir=" + backupDir.getAbsolutePath())
        .add("-mesos_master_address=fake")
        .add("-thermos_executor_path=fake")
        .add("-http_port=8081")
        .build();

    AppLauncher.launch(LocalSchedulerMain.class, arguments.toArray(new String[0]));
  }
}
