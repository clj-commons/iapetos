# iapetos

__iapetos__ is a Clojure wrapper around the [Prometheus Java
Client][java-client].

[java-client]: https://github.com/prometheus/client_java

## Usage

Don't. This is mostly me exploring Prometheus.

### Basic Usage

Metrics have to be declared on a collector registry before being used:

```clojure
(require '[iapetos.core :as prometheus])

(defonce registry
  (-> (prometheus/collector-registry)
      (prometheus/register
        (prometheus/histogram :app/duration-seconds)
        (prometheus/gauge     :app/last-success-unixtime {:lazy? true})
        (prometheus/gauge     :app/active-users-total    {:lazy? true})
        (prometheus/counter   :app/runs-total))))
```

Now, you can write an instrumented function using some of iapetos' helper macros
or by operating directly on the collectors:

```clojure
(defn run
  []
  (prometheus/inc registry :app/runs-total)
  (prometheus/with-duration (registry :app/duration-seconds)
    (prometheus/with-success-timestamp (registry :app/last-success-unixtime)
      ...
      (prometheus/set registry :app/active-users-total (count-users!))
      true)))
```

The metrics can then be either exported using a textual representation:

```clojure
(require '[iapetos.export :as export])
(print (export/text-format registry))
;; # HELP app_active_users_total a gauge metric.
;; # TYPE app_active_users_total gauge
;; app_active_users_total 10.0
;; # HELP app_last_success_unixtime a gauge metric.
;; # TYPE app_last_success_unixtime gauge
;; app_last_success_unixtime 1.469284587819E9
;; ...
```

Or pushed to the respective Prometheus gateway:

```clojure
(export/push! registry {:gateway "push-gateway:12345"})
```

### More

Soon.

## License

```
MIT License

Copyright (c) 2016 Yannick Scherer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
