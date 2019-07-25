# Version 0.1.9

# Breaking Changes

- clojars group change: move from iapetos to `clj-commons/iapetos` (https://clojars.org/clj-commons/iapetos)

  This change was needed to continue publishing new clojars to `clj-commons` after project migration.

# Changes:

- update deps

- introduce changelog file.md


# Version 0.1.8

# Breaking Changes

None.

# Deprecation

    The :lazy? flag on collectors is now deprecated, use register-lazy instead.

# Features

    upgrades the Prometheus Java dependencies to version 0.2.0.
    introduces register-lazy as a replacement for the :lazy? flag on collectors.
    introduces clear and unregister functions to remove collectors from a registry they were previously added to (see #10).
