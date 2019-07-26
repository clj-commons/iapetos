# Version 0.1.9: First clj-commons Release

## Breaking Changes

- clojars group change: move from iapetos to `clj-commons/iapetos`(https://clojars.org/clj-commons/iapetos)

  This change was needed to continue publishing new clojars to `clj-commons` after project migration.

## Changes:

- updates Prometheus java dependencies to 0.6.0
- introduce CHANGELOG.md


# Version 0.1.8: Unregister/Clear Collectors

## Breaking Changes

None.

## Deprecation

- The `:lazy?` flag on collectors is now deprecated, use `register-lazy` instead.

## Features

- upgrades the Prometheus Java dependencies to version 0.2.0.
- introduces `register-lazy` as a replacement for the `:lazy?` flag on collectors.
- introduces `clear` and `unregister` functions to remove collectors from a registry they
  were previously added to (see #10).


# Version 0.1.7: Default Registry

## Breaking Changes

None.

## Features

- upgrades the Prometheus Java dependencies to version 0.0.26.
- allows access to the default registry using iapetos.core/default-registry (see #10).
- allows "wrapping" of an existing registry using pushable-collector-registry to make it pushable.


# Version 0.1.6: Summary Quantiles

## Breaking Changes

None.

## Features

- allows specification of :quantiles when creating a summary collector (see #6).


# Version 0.1.5: Ring Latency Buckets

## Breaking Changes

None.

## Bugfixes

- no longer ignores :latency-histogram-buckets option in Ring collector (see #5).


# Version 0.1.4: Ring Collector Labels

## Breaking Changes

None.

## Features

- allows adding of additional labels to the Ring collector (see #4, thanks to @psalaberria002).


# Version 0.1.3: Java Simple Client Upgrade

## Breaking Changes

None.

## Dependencies

This release upgrades the Java client dependencies to the latest versions.


# Version 0.1.2: Add Request Hook to Ring Middlewares

## Breaking Changes

None.

## Features

- adds a :on-request hook to the wrap-metrics and wrap-metrics-expose middleware (see #2).


# Version 0.1.1: Fix 'wrap-metrics' Middleware

## Breaking Changes

None.

## Bugfixes

- fixes passing of options from wrap-metrics to wrap-instrumentation, allowing for setting of :path-fn.


# Version 0.1.0: Initial Release

This is the initial release of iapetos, a Clojure [Prometheus](https://prometheus.io/) client.
