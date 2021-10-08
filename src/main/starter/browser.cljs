(ns starter.browser
  (:require
    ["@xstate/inspect" :as xsi]
    ["xstate" :as xstate]
    [applied-science.js-interop :as j]
    [starter.xstate :as xs]))


;; API via React Context
;; (use-popover? :my_popover_id) => true or false
;; on mount this sends a REQUEST_POPOVER event
;; on unmount UNREQUEST_POPOVER
;; hook returns true if popover should be shown

;; Statechart could live in react context or global application state just exporting a single "show this" via React context

;; Whats missing?
;; - Updates to seen that occur from different client / outside the state machine
;; - Could be extended to allow a "force show" that puts showing items back on queue

(def counter-machine
  (xstate/createMachine
    #js {:id "counter-machine"
         :initial "start"
         :context (xs/context {:some {:count 0}})
         :states (clj->js {"start" {:on {:INC {:actions [:increment!]}
                                         :DEC {:actions [:decrement!]}}}})}
    (clj->js {:actions {:decrement! (xs/assign (fn [context event]
                                                 (js/console.log :dec-event (pr-str event))
                                                 (update-in context [:some :count] dec)))
                        :increment! (xs/assign (fn [context event]
                                                 (js/console.log :inc-event (pr-str event))
                                                 (update-in context [:some :count] inc)))}})))


;; The first action sucessfully updates the initial context, a second update
;; then receives a context that has been "meddled with", i.e. it no longer is
;; compatible with the various Clojure functions that would usually operate on
;; maps.


(def popoverseq-machine
  (xstate/createMachine
    #js {:id "popoversequencer"
         :initial "idle"
         :context (xs/context {:seen #{}
                               :showing nil
                               :queue []})
         ;; order of actions:
         ;; (1) exit of current state (2) actions of transition (3) entry of next state
         :states (clj->js {"idle" {:on {:INITIALIZE {:target "ready"
                                                     :actions [:initialize-seen!]}
                                        :REQUEST_POPOVER {:cond :should-queue?
                                                          :actions [:queue-popover!]}}}
                           "ready" {:entry (xstate/send "SHOW_POPOVER")

                                    :on {:REQUEST_POPOVER [{:cond :should-queue?
                                                            :actions [:queue-popover! (xstate/send "SHOW_POPOVER")]}]
                                         :SHOW_POPOVER [{:cond :popover-available?
                                                         :target "showing-popover"
                                                         :actions [:show-popover!]}]}}

                           "showing-popover" {:on {:DISMISS [{:cond :queue-empty?
                                                              :target "ready"
                                                              :actions [:dismiss-popover! :unset-showing!]}
                                                             {:cond :popover-available?
                                                              :actions [:dismiss-popover! :show-popover!]}]
                                                   :REQUEST_POPOVER {:cond :should-queue?
                                                                     :actions [:queue-popover!]}
                                                   :UNREQUEST_POPOVER [{:cond :currently-showing?
                                                                        :target "ready"
                                                                        :actions [:unset-showing!]}
                                                                       {:actions [:unqueue-popover!]}]}}})}
    ;; Important that the list of actions is provided as second arg!
    ;; Error handling for missing actions is not great
    (-> #js {}

        (xs/add-guards! {:queue-empty? (fn [context _event]
                                         (empty? (:queue context)))
                         :should-queue? (fn [{:keys [seen showing queue]} {:keys [popover-id]}]
                                          (and (not= showing popover-id) ; not showing
                                               (not (contains? seen popover-id)) ; not seen
                                               (not (contains? (set queue) popover-id)))) ; not in queue
                         :popover-available? (fn [{:keys [queue]} _event]
                                               (boolean (seq queue)))
                         :currently-showing? (fn [{:keys [showing]} {:keys [popover-id]}]
                                               (= showing popover-id))})

        (xs/add-actions! {:unset-showing! (fn [context _event]
                                            (assoc context :showing nil))
                          :show-popover! (fn [{:keys [queue] :as context} _event]
                                           (-> context
                                               ;; TODO insert fancy logic to pick the most important popover from the queue
                                               (assoc :showing (first queue))
                                               (update :queue (comp vec rest))))
                          :dismiss-popover! (fn [{:keys [showing] :as context} _event]
                                              (assert showing)
                                              (update context :seen conj showing))
                          :unqueue-popover! (fn [context {:keys [popover-id]}]
                                              (update context :queue #(vec (remove #{popover-id} %))))
                          :queue-popover! (fn [context event]
                                            (update context :queue conj (:popover-id event)))
                          :initialize-seen! (fn [context event]
                                              (let [seen? (set (:seen-popover-ids event))]
                                                (-> context
                                                    (update :queue #(vec (remove seen? %)))
                                                    (assoc :seen seen?))))}))))


(defonce popoverseq-service
  (-> popoverseq-machine
      (xstate/interpret #js {:devTools true})
      (.onTransition (fn [state]
                       (js/console.log :transition (pr-str (j/get-in state [:context xs/context-key])))))))


;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start
  []
  (js/console.log "start")
  (.start popoverseq-service))


(defn init
  []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (js/console.log "init")
  (xsi/inspect #js {:url "https://statecharts.io/inspect"})
  (start))


;; this is called before any code is reloaded
(defn ^:dev/before-load stop
  []
  (js/console.log "stop"))


(comment
 (require '[shadow.cljs.devtools.api :as shadow])
 (shadow/repl :app)

 (js/console.log popoverseq-service)

 (.send popoverseq-service #js {:type "INC" :some "data"})
 (.send popoverseq-service #js {:type "DEC" :foo "bar"})

 (.send popoverseq-service #js {:type "DISMISS"})
 (.send popoverseq-service #js {:type "INITIALIZE" :seen-popover-ids #{}})

 (.send popoverseq-service (clj->js {:type "REQUEST_POPOVER" :popover-id "1"}))
 (.send popoverseq-service (clj->js {:type "REQUEST_POPOVER" :popover-id "2"}))
 (.send popoverseq-service (clj->js {:type "REQUEST_POPOVER" :popover-id "3"}))
 (.send popoverseq-service (clj->js {:type "REQUEST_POPOVER" :popover-id "4"}))

 (.send popoverseq-service (clj->js {:type "UNREQUEST_POPOVER" :popover-id "1"}))
 (.send popoverseq-service (clj->js {:type "UNREQUEST_POPOVER" :popover-id "2"}))
 (.send popoverseq-service (clj->js {:type "UNREQUEST_POPOVER" :popover-id "3"}))

 )
