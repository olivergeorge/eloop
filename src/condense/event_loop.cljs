(ns condense.event-loop)

(def registry-ref
  (atom {:std-ins []
         :error   (partial js/console.error)}))

(defn cfg [k v] (swap! registry-ref assoc k v))
(defn reg [{:keys [id] :as m}] (swap! registry-ref assoc-in [:handlers id] m))

(defn do-preloads
  [{:keys [std-ins handlers event] :as ctx}]
  (let [eid (first event)
        kps (for [[id & args] (into std-ins (get-in handlers [eid :ins]))
                  :let [preload (get-in handlers [id :preload])]
                  :when preload]
              [id (apply preload args)])]
    (-> (js/Promise.all (map second kps))
        (.then (fn [vs] (assoc ctx :preloads (zipmap (map first kps) vs)))))))

(defn do-event
  [{:keys [std-ins handlers event preloads] :as ctx}]
  (letfn [(do-input [[id & args]]
            (or (some-> (get-in handlers [id :input]) (apply ctx args))
                (get preloads id)))
          (do-transition [[id & args]]
            (some-> (get-in handlers [id :transition]) (apply ctx args)))]
    (let [[eid & args] event
          logic (get-in handlers [eid :logic] identity)
          ins (into std-ins (get-in handlers [eid :ins]))
          s (zipmap (map first ins) (map do-input ins))
          s' (apply logic s args)]
      (doall (map do-transition s')))))

(defn do-effects
  [{:keys [error handlers]} ms]
  (doseq [m ms [id & args :as v] m]
    (try (apply (get-in handlers [id :effect] #()) args)
         (catch js/Error err (error ::do-effects.err v err)))))

(defn dispatch [event]
  (let [{:keys [error] :as ctx} @registry-ref]
    (-> (do-preloads (assoc ctx :event event))
        (.then do-event)
        (.catch (fn [err] (error ::dispatch.err err))))))

(defn dispatch-sync [event]
  (do-event (assoc @registry-ref :event event)))

(comment
  (def app-db (r/atom {}))
  (cfg :std-ins [[:db] [:event]])
  (reg {:id :db :input #(deref app-db) :transition #(reset! app-db %2)})
  (reg {:id :fx :transition do-effects})
  (reg {:id :event :input :event})
  (reg {:id :dispatch :effect dispatch})
  (dispatch [:bootstrap]))