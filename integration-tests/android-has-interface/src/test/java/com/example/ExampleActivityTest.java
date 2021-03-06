package com.example;

import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static androidx.lifecycle.Lifecycle.State.CREATED;
import static androidx.test.core.app.ActivityScenario.launch;
import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class) //
public final class ExampleActivityTest {
  @Test public void activityInjection() {
    try (ActivityScenario<ExampleActivity> scenario = launch(ExampleActivity.class)) {
      scenario.moveToState(CREATED);
      scenario.onActivity(activity -> {
        assertThat(activity.string).isEqualTo("Hello!");
        assertThat(activity.aLong).isEqualTo(10L);
        assertThat(activity.anInt).isEqualTo(20);
      });
    }
  }
}
