package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;

import java.net.URI;
import java.time.Duration;

/**
 * Contains methods for configuring the polling data source.
 * <p>
 * Polling is not the default behavior; by default, the SDK uses a streaming connection to receive feature flag
 * data from LaunchDarkly. In polling mode, the SDK instead makes a new HTTP request to LaunchDarkly at regular
 * intervals. HTTP caching allows it to avoid redundantly downloading data if there have been no changes, but
 * polling is still less efficient than streaming and should only be used on the advice of LaunchDarkly support.
 * <p>
 * To use polling mode, create a builder with {@link Components#pollingDataSource()},
 * change its properties with the methods of this class, and pass it to {@link com.launchdarkly.sdk.server.LDConfig.Builder#dataSource(DataSourceFactory)}:
 * <pre><code>
 *     LDConfig config = new LDConfig.Builder()
 *         .dataSource(Components.pollingDataSource().pollInterval(Duration.ofSeconds(45)))
 *         .build();
 * </code></pre>
 * <p>
 * Note that this class is abstract; the actual implementation is created by calling {@link Components#pollingDataSource()}.
 * 
 * @since 4.12.0
 */
public abstract class PollingDataSourceBuilder implements DataSourceFactory {
  /**
   * The default and minimum value for {@link #pollInterval(Duration)}: 30 seconds.
   */
  public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(30);
  
  protected URI baseURI;
  protected Duration pollInterval = DEFAULT_POLL_INTERVAL;

  /**
   * Sets a custom base URI for the polling service.
   * <p>
   * You will only need to change this value in the following cases:
   * <ul>
   * <li> You are using the <a href="https://docs.launchdarkly.com/home/relay-proxy">Relay Proxy</a>. Set
   *   {@code streamUri} to the base URI of the Relay Proxy instance.
   * <li> You are connecting to a test server or anything else other than the standard LaunchDarkly service.
   * </ul>
   * 
   * @param baseURI the base URI of the polling service; null to use the default
   * @return the builder
   */
  public PollingDataSourceBuilder baseURI(URI baseURI) {
    this.baseURI = baseURI;
    return this;
  }
  
  /**
   * Sets the interval at which the SDK will poll for feature flag updates.
   * <p>
   * The default and minimum value is {@link #DEFAULT_POLL_INTERVAL}. Values less than this will be
   * set to the default.
   * 
   * @param pollInterval the polling interval; null to use the default 
   * @return the builder
   */
  public PollingDataSourceBuilder pollInterval(Duration pollInterval) {
    if (pollInterval == null) {
      this.pollInterval = DEFAULT_POLL_INTERVAL;
    } else {
      this.pollInterval = pollInterval.compareTo(DEFAULT_POLL_INTERVAL) < 0 ? DEFAULT_POLL_INTERVAL : pollInterval;
    }
    return this;
  }
}
