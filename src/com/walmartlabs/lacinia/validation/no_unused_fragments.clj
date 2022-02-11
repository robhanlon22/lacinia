; Copyright (c) 2017-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns com.walmartlabs.lacinia.validation.no-unused-fragments
  {:no-doc true}
  (:require
    [clojure.set :as set]
    [com.walmartlabs.lacinia.internal-utils :refer [q cond-let]])
  (:import (clojure.lang PersistentQueue)))

(defn ^:private all-fragments-used
  [fragments root-selections]
  (loop [result (transient #{})
         queue (-> (PersistentQueue/EMPTY)
                   (into root-selections)
                   (into (vals fragments)))]
    (cond-let

      :let [selection (peek queue)]

      (nil? selection)
      (persistent! result)

      :let [{:keys [fragment-name]} selection
            queue' (pop queue)]

      ;; Named fragments do not, themselves, have sub-selections
      fragment-name
      (recur (conj! result fragment-name) queue')

      :let [sub-selections (:selections selection)]

      (seq sub-selections)
      (recur result (into queue' sub-selections))

      :else
      (recur result queue'))))

(defn no-unused-fragments
  "Validates if all fragment definitions are spread
  within operations, or spread within other fragments
  spread within operations."
  [prepared-query]
  (let [{:keys [fragments selections]} prepared-query
        f-locations (into {} (map (fn [[f-name {location :location}]]
                                    {f-name location})
                                  fragments))
        f-definitions (set (keys fragments))
        f-names-used (all-fragments-used fragments selections)]
    (for [unused-f-definition (set/difference f-definitions f-names-used)]
      {:message (format "Fragment %s is never used."
                        (q unused-f-definition))
       :locations [(unused-f-definition f-locations)]})))
