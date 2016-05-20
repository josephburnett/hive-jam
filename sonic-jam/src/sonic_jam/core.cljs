(ns sonic-jam.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [chan put! <! >!]]
            [chord.client :refer [ws-ch]]))

(enable-console-print!)

(defonce app-state (atom {:grids {}
                          :samples []
                          :synths []
                          :grid-types ["synth" "sample"]}))

(defn grids []
  (om/ref-cursor (:grids (om/root-cursor app-state))))

(defn grid-types []
  (om/ref-cursor (:grid-types (om/root-cursor app-state))))

(defn samples []
  (om/ref-cursor (:samples (om/root-cursor app-state))))

(defn synths []
  (om/ref-cursor (:synths (om/root-cursor app-state))))

(defn handle-change [e owner state key]
  (om/set-state! owner key (.. e -target -value)))

(defn cell-view [{:keys [cursor id]} _]
  (reify
    om/IRenderState
    (render-state [_ state]
      (dom/td #js {:onClick #(do
                               (om/update! cursor [(mod (inc (first cursor)) 2)])
                               (go (>! (:set-state-ch state) id)))
                   :style #js {:width "20px"
                               :fontSize "20px"}}
             (str " " (first cursor))))))

(def style-grey #js {:style #js {:color "#999"}})

(defn new-id []
  (let [letters (map char (range 97 123))]
    (apply str (take 32 (repeatedly #(rand-nth letters))))))

(declare grid-view)

(defn param-editor-builder [key]
  (fn [{:keys [cursor id set-state-ch]} owner]
    (reify
      om/IInitState
      (init-state [_]
        {:state :init})
      om/IRenderState
      (render-state [this state]
        (let [cursor (get cursor key)]
          (let [commit #(do
                          (om/update-state! owner (fn [s] (merge s {:state :open})))
                          (om/transact! cursor (fn [c] (assoc c (:field state) (:text state))))
                          (go (>! set-state-ch id)))
                closer #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :init})))}
                canceller #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :open})))}]
            (condp = (:state state)
              :init (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :open})))} "{..}")
              :open (dom/table nil
                               (apply dom/tbody nil
                                      (concat
                                       [(dom/tr nil
                                                (dom/td closer "{")
                                                (dom/td nil nil)
                                                (dom/td nil nil))]
                                       (map (fn [k v] (dom/tr nil
                                                              (dom/td nil nil)
                                                              (dom/td nil (str k ":"))
                                                              (dom/td #js {:onClick #(om/update-state! owner
                                                                                                       (fn [s] (merge s {:state :editing
                                                                                                                         :text v
                                                                                                                         :field k})))}
                                                                      v)))
                                            (keys cursor)
                                            (vals cursor))
                                       [(dom/tr nil
                                                (dom/td nil nil)
                                                (dom/td #js {:onClick #(om/update-state! owner
                                                                                         (fn [s] (merge s {:state :adding-field
                                                                                                           :text nil
                                                                                                           :field ""})))}
                                                        "[+]")
                                                (dom/td nil nil))
                                        (dom/tr nil
                                                (dom/td closer "}")
                                                (dom/td nil nil)
                                                (dom/td nil nil))])))
              :editing (dom/table nil
                                  (apply dom/tbody nil
                                         (concat
                                          [(dom/tr closer
                                                   (dom/td closer "{")
                                                   (dom/td nil nil)
                                                   (dom/td nil nil))]
                                          (map (fn [k v] (dom/tr nil
                                                                 (dom/td nil (str k ":"))
                                                                 (if (= k (:field state))
                                                                   (dom/td nil
                                                                           (dom/input #js {:type "text" :value (:text state)
                                                                                           :onChange #(handle-change % owner state :text)
                                                                                           :onKeyDown #(when (= 13 (.-which %))
                                                                                                         (commit))}))
                                                                   (dom/td #js {:onClick #(om/update-state! owner
                                                                                                            (fn [s] (merge s {:state :editing
                                                                                                                              :text v
                                                                                                                              :field k})))}
                                                                           v))))
                                               (keys cursor)
                                               (vals cursor))
                                          [(dom/tr nil
                                                   (dom/td nil nil)
                                                   (dom/td #js {:onClick #(om/update-state! owner
                                                                                            (fn [s] (merge s {:state :adding-field
                                                                                                              :text nil
                                                                                                              :field ""})))}
                                                           "[+]")
                                                   (dom/td nil nil))
                                           (dom/tr nil
                                                   (dom/td closer "}")
                                                   (dom/td nil nil)
                                                   (dom/td nil nil))])))
              :adding-field (dom/table nil
                                       (concat
                                        [(dom/tr nil
                                                 (dom/td closer "{")
                                                 (dom/td nil nil)
                                                 (dom/td nil nil))]
                                        (map (fn [k v] (dom/tr nil
                                                               (dom/td nil nil)
                                                               (dom/td nil (str k ":"))
                                                               (dom/td #js {:onClick #(om/update-state! owner
                                                                                                        (fn [s] (merge s {:state :editing
                                                                                                                          :text v
                                                                                                                          :field k})))}
                                                                       v)))
                                             (keys cursor)
                                             (vals cursor))
                                        [(dom/tr nil
                                                 (dom/td nil nil)
                                                 (dom/td nil
                                                         (dom/span canceller "[- ")
                                                         (dom/input #js {:type "text" :value (:field state)
                                                                         :onChange #(handle-change % owner state :field)
                                                                         :onKeyDown #(when (= 13 (.-which %))
                                                                                       (om/update-state! owner
                                                                                                         (fn [s] (merge {:state :adding-value
                                                                                                                         :field (:field state)}))))}))
                                                 (dom/td nil " ]"))
                                         (dom/tr nil
                                                 (dom/td closer "}")
                                                 (dom/td nil nil)
                                                 (dom/td nil nil))]))
              :adding-value (dom/table nil
                                       (concat
                                        [(dom/tr nil
                                                 (dom/td closer "}")
                                                 (dom/td nil nil)
                                                 (dom/td nil nil))]
                                        (map (fn [k v] (dom/tr nil
                                                               (dom/td nil nil)
                                                               (dom/td nil (str k ":"))
                                                               (dom/td #js {:onClick #(om/update-state! owner
                                                                                                        (fn [s] (merge s {:state :editing
                                                                                                                          :text v
                                                                                                                          :field k})))}
                                                                       v)))
                                             (keys cursor)
                                             (vals cursor))
                                        [(dom/tr nil
                                                 (dom/td nil nil)
                                                 (dom/td nil
                                                         (dom/span canceller "[- ")
                                                         (dom/span nil (str (:field state) ":")))
                                                 (dom/td nil
                                                         (dom/input #js {:type "text" :value (:text state)
                                                                         :onChange #(handle-change % owner state :text)
                                                                         :onKeyDown #(when (= 13 (.-which %))
                                                                                       (commit))})
                                                         (dom/span nil " ]")))
                                         (dom/tr nil
                                                 (dom/td closer "}")
                                                 (dom/td nil nil)
                                                 (dom/td nil nil))])))))))))

(def param-editor
  (param-editor-builder "params"))

(def synth-param-editor
  (param-editor-builder "synth-params"))

(def sample-param-editor
  (param-editor-builder "sample-params"))

(defn type-editor [{:keys [cursor id set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state (if (= "none" (get cursor "type"))
                :selecting
                :immutable)
       :type (get cursor "type")})
    om/IRenderState
    (render-state [this state]
      (condp = (:state state)
        :selecting (dom/select #js {:value (:type state)
                                    :onChange #(handle-change % owner state :type)
                                    :onClick #(handle-change % owner state :type)
                                    :onKeyDown #(when (= 13 (.-which %))
                                                  (om/update-state! owner (fn [s] (merge s {:state :immutable})))
                                                  (if-not (= "grid" (:type state))
                                                    (om/transact! cursor (fn [c] (assoc c "type" (:type state))))
                                                    (let [grid-id (new-id)
                                                          grid {"name" ""
                                                                "id" grid-id
                                                                "bpc" 1
                                                                "tracks" []}]
                                                      (om/transact! cursor (fn [c] (assoc c
                                                                                          "type" (:type state)
                                                                                          "synth-params" {}
                                                                                          "sample-params" {}
                                                                                          "id" grid-id)))
                                                      (om/transact! (om/observe owner (grids))
                                                                    (fn [c] (assoc c (get grid "id") grid)))
                                                      (go (>! set-state-ch grid-id))))
                                                  (go (>! set-state-ch id)))}
                               (dom/option nil "synth")
                               (dom/option nil "sample")
                               (dom/option nil "play")
                               (dom/option nil "grid"))
        :immutable (dom/span nil (get cursor "type"))))))

(defn select-editor-builder [key options-ref]
  (fn [{:keys [cursor id set-state-ch]} owner]
    (reify
      om/IInitState
      (init-state [_]
        {:state :init})
      om/IRenderState
      (render-state [_ state]
        (if (and (= :init (:state state))
                 (contains? cursor key))
          (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :selecting})))} (get cursor key))
          (apply dom/select #js {:value ((keyword key) state)
                                 :onChange #(handle-change % owner state (keyword key))
                                 :onClick #(handle-change % owner state (keyword key))
                                 :onKeyDown #(when (= 13 (.-which %))
                                               (om/update-state! owner (fn [s] (merge s {:state :init})))
                                               (om/transact! cursor (fn [c] (assoc c key ((keyword key) state))))
                                               (go (>! set-state-ch id)))}
                 (map #(dom/option nil %) (om/observe owner (options-ref)))))))))

(def grid-type-editor
  (select-editor-builder "grid-type" grid-types))

(def synth-editor
  (select-editor-builder "synth" synths))

(def grid-synth-editor
  (select-editor-builder "grid-synth" synths))

(def sample-editor
  (select-editor-builder "sample" samples))

(def grid-sample-editor
  (select-editor-builder "grid-sample" samples))

(defn fx-editor [{:keys [cursor id set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [_ state]
      (let [transition #(om/update-state! owner (fn [s] (merge {:state %})))]
        (condp = (:state state)
          :init (dom/span #js {:onClick #(transition :open)}
                          "{..}")
          :open (dom/table nil
                           (apply dom/tbody nil
                                  (concat
                                   [(dom/tr nil
                                            (dom/td #js {:onClick #(transition :init)} "{")
                                            (dom/td nil nil)
                                            (dom/td nil nil))]
                                   (map #(dom/tr nil
                                                 (dom/td nil nil)
                                                 (dom/td nil (get % "fx"))
                                                 (dom/td nil (om/build param-editor
                                                                       {:cursor %
                                                                        :id id
                                                                        :set-state-ch set-state-ch})))
                                        cursor)
                                   [(dom/tr nil
                                            (dom/td nil nil)
                                            (dom/td #js {:onClick #(transition :open)} "[+]") ; TODO(:adding)
                                            (dom/td nil nil))
                                    (dom/tr nil
                                            (dom/td #js {:onClick #(transition :init)} "}")
                                            (dom/td nil nil)
                                            (dom/td nil nil))]))))))))

(defn track-editor [{:keys [cursor id delete-ch set-state-ch] :as input} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [this state]
      (let [closer #js {:onClick  #(om/update-state! owner (fn [s] (merge s {:state :init})))}]
        (condp = (:state state)
          :init (dom/p style-grey
                       (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :open})))} "{..}"))
          :open (let [fx-row (dom/tr nil
                                     (dom/td nil nil)
                                     (dom/td nil "fx:")
                                     (dom/td nil (om/build fx-editor {:cursor (get cursor "fx")
                                                                      :id id
                                                                      :set-state-ch set-state-ch})))]
                  (dom/p style-grey
                         (dom/table nil
                                    (apply dom/tbody nil
                                           (concat
                                            [(dom/tr nil
                                                     (dom/td nil nil)
                                                     (dom/td nil nil)
                                                     (dom/td nil (dom/span #js {:onClick #(go (>! delete-ch cursor))
                                                                                :style #js {:float "right"}} "X")))
                                             (dom/tr nil
                                                     (dom/td closer "{")
                                                     (dom/td nil nil)
                                                     (dom/td nil nil))
                                             (dom/tr nil
                                                     (dom/td nil nil)
                                                     (dom/td nil "type:")
                                                     (dom/td nil (om/build type-editor input)))]
                                            (condp = (get cursor "type")
                                              "synth" [(dom/tr nil
                                                               (dom/td nil nil)
                                                               (dom/td nil "synth:")
                                                               (dom/td nil (om/build synth-editor input)))]
                                              "sample" [(dom/tr nil
                                                                (dom/td nil nil)
                                                                (dom/td nil "sample:")
                                                                (dom/td nil (om/build sample-editor input)))]
                                              "play" [(dom/tr nil
                                                              (dom/td nil nil)
                                                              (dom/td nil "note:")
                                                              (dom/td nil (get cursor "note")))]
                                              [])
                                            (condp = (get cursor "type")
                                              "none" []
                                              "grid" (condp = (get cursor "grid-type")
                                                       "synth" [(dom/tr nil
                                                                        (dom/td nil nil)
                                                                        (dom/td nil "grid-type:")
                                                                        (dom/td nil (om/build grid-type-editor input)))
                                                                (dom/tr nil
                                                                        (dom/td nil nil)
                                                                        (dom/td nil "synth:")
                                                                        (dom/td nil (om/build grid-synth-editor input)))
                                                                (dom/tr nil
                                                                        (dom/td nil nil)
                                                                        (dom/td nil "params:")
                                                                        (dom/td nil (om/build synth-param-editor input)))
                                                                fx-row]
                                                       "sample" [(dom/tr nil
                                                                         (dom/td nil nil)
                                                                         (dom/td nil "grid-type:")
                                                                         (dom/td nil (om/build grid-type-editor input)))
                                                                 (dom/tr nil
                                                                         (dom/td nil nil)
                                                                         (dom/td nil "sample:")
                                                                         (dom/td nil (om/build grid-sample-editor input)))
                                                                 (dom/tr nil
                                                                         (dom/td nil nil)
                                                                         (dom/td nil "params:")
                                                                         (dom/td nil (om/build sample-param-editor input)))
                                                                 fx-row]
                                                       [(dom/tr nil
                                                                (dom/td nil nil)
                                                                (dom/td nil "grid-type:")
                                                                (dom/td nil (om/build grid-type-editor input)))
                                                        fx-row])
                                              [(dom/tr nil
                                                       (dom/td nil nil)
                                                       (dom/td nil "params:")
                                                       (dom/td nil (om/build param-editor input)))
                                               fx-row])
                                            [(dom/tr nil
                                                     (dom/td closer "}")
                                                     (dom/td nil nil)
                                                     (dom/td nil))]))))))))))

(defn track-builder [{:keys [cursor id set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [_ state]
      (let [transition #(om/update-state! owner (fn [s] (merge s %)))
            set-width #(om/transact!
                        cursor (fn [s]
                                 (clj->js
                                  (conj s {"type" "none"
                                           "beats" (repeat % [0])
                                           "params" {}}))))
            width-selection (fn [w s]
                              (dom/span #js {:onClick
                                             #(do
                                                (set-width w)
                                                (transition {:state :init})
                                                (go (>! set-state-ch id)))} s))]
        (condp = (:state state)
          :init (let [width (apply max (map #(count (get % "beats")) cursor))]
                  (if (= 0 (count cursor))
                    (dom/p style-grey
                           (dom/span #js {:onClick #(transition {:state :open})} "[+]"))
                    (dom/p style-grey
                           (dom/span #js {:onClick #(transition {:state :open})} "[+")
                           (width-selection width (str " " width))
                           (dom/span nil "]"))))
          :open (dom/p style-grey
                       (dom/span #js {:onClick #(transition {:state :init})} "[- ")
                       (width-selection 1 " 1")
                       (width-selection 2 " 2")
                       (width-selection 4 " 4")
                       (width-selection 8 " 8")
                       (width-selection 16 " 16")
                       (dom/span nil "]")))))))

(defn track-view [{:keys [cursor id delete-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:track-expanded true})
    om/IWillMount
    (will-mount [_]
      (when (= "grid" (get cursor "type"))
        (go (>! (om/get-state owner :get-state-ch) (get cursor "id")))))
    om/IRenderState
    (render-state [_ state]
      (if-not (:track-expanded state)
        (dom/tr nil (dom/td #js {:onClick #(om/set-state! owner :track-expanded true)
                                 :style #js {:color "#999"}} ">"))
        (dom/tr nil
                (dom/td nil
                 (dom/table nil
                  (apply dom/tr nil
                         (let [cursors (map #(hash-map :cursor %1 :id %2)
                                            (get cursor "beats")
                                            (repeat id))]
                           (om/build-all cell-view cursors {:state state})))))
                (dom/td #js {:style #js {:color "#999" :paddingRight "10px"}}
                        (om/build track-editor {:cursor cursor :id id :delete-ch delete-ch
                                                :set-state-ch (:set-state-ch state)}))
                (dom/td nil
                        (when (= "grid" (get cursor "type"))
                          (let [id (get cursor "id")
                                sub-state {:set-state-ch (:set-state-ch state)
                                           :get-state-ch (:get-state-ch state)}]
                            (om/build grid-view id {:state sub-state})))))))))

(defn grid-editor [{:keys [id cursor set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init
       :bpc (get cursor "bpc")})
    om/IRenderState
    (render-state [_ state]
      (let [transition #(om/update-state! owner (fn [s] (merge s %)))
            closer #(dom/span #js {:onClick (fn [] (transition {:state :init}))} %)
            double-width #(let [tracks (get cursor "tracks")
                                doubled-tracks (map (fn [t]
                                                      (let [beats (get t "beats")]
                                                        (assoc t "beats"
                                                               (take (* 2 (count beats))
                                                                     (cycle beats)))))
                                                    tracks)]
                            (om/transact! cursor (fn [c] (assoc cursor "tracks" (clj->js doubled-tracks))))
                            (go (>! set-state-ch id)))
            half-width #(let [tracks (get cursor "tracks")
                              halved-tracks (map (fn [t]
                                                   (let [beats (get t "beats")]
                                                     (assoc t "beats"
                                                            (take (int (/ (count beats) 2)) beats))))
                                                 tracks)]
                          (print tracks)
                          (print halved-tracks)
                          (om/transact! cursor (fn [c] (assoc cursor "tracks" (clj->js halved-tracks))))
                          (go (>! set-state-ch id)))]
        (condp = (:state state)
          :init (dom/span #js {:onClick #(transition {:state :open})}
                          "{..}")
          :open (dom/span nil
                          (closer "{")
                          (dom/span nil
                                    (dom/span nil " bpc: ")
                                    (dom/span #js {:onClick #(transition {:state :editing-bpc})}
                                              (str (get cursor "bpc"))))
                          (dom/span nil
                                    (dom/span nil " width: ")
                                    (dom/span #js {:onClick double-width} "+")
                                    (dom/span nil "/")
                                    (dom/span #js {:onClick half-width} "-"))
                          (dom/span nil " }"))
          :editing-bpc (dom/span nil
                                 (closer "{ ")
                                 (dom/span nil
                                           (dom/span nil "bpc: "))
                                 (dom/input #js {:type "text" :value (:bpc state)
                                                 :onChange #(handle-change % owner state :bpc)
                                                 :onKeyDown #(when (= 13 (.-which %))
                                                               (transition {:state :open})
                                                               (om/transact! cursor (fn [c] (assoc c "bpc"(:bpc state))))
                                                               (go (>! set-state-ch id)))})
                                 (dom/span nil " }")))))))

(defn grid-view [id owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [delete-ch (chan)]
        {:grid-expanded false
         :delete-ch delete-ch}))
    om/IWillMount
    (will-mount [this]
      (let [delete-ch (:delete-ch (om/get-state owner))
            set-state-ch (:set-state-ch (om/get-state owner))]
        (go-loop []
          (let [track (<! delete-ch)
                cursor (get-in (om/observe owner (grids)) [id "tracks"])]
            (om/update! cursor (vec (remove (partial = track) cursor)))
            (put! set-state-ch id)
            (recur)))))
    om/IRenderState
    (render-state [_ state]
      (let [cursor (get (om/observe owner (grids)) id)
            name (get cursor "name")]
        (dom/div #js {:style #js {:borderLeft "solid 3px #ccc"
                                  :paddingLeft "10px"}}
                 (if-not (:grid-expanded state)
                   (dom/div nil 
                            (dom/p style-grey
                                   (dom/span #js {:onClick #(om/set-state! owner :grid-expanded true)} "+")))
                   (dom/div nil 
                            (dom/p style-grey
                                   (dom/span #js {:onClick #(om/set-state! owner :grid-expanded false)}
                                             (str "- "))
                                   (dom/span nil (str name " "))
                                   (om/build grid-editor {:id id
                                                          :set-state-ch (:set-state-ch state)
                                                          :cursor cursor}))
                            (dom/table nil
                                       (apply dom/tbody nil
                                              (let [cursors (map #(hash-map :cursor %1 :id %2 :delete-ch %3)
                                                                 (get cursor "tracks")
                                                                 (repeat id)
                                                                 (repeat (:delete-ch state)))]
                                                (om/build-all track-view cursors {:state state}))))
                            (om/build track-builder {:cursor (get cursor "tracks")
                                                     :id id
                                                     :set-state-ch (:set-state-ch state)}))))))))

(defn app-view [cursor _]
  (reify
    om/IInitState
    (init-state [_]
      (let [set-state-ch (chan)
            get-state-ch (chan)]
        (go
          (let [{:keys [ws-channel error]} (<! (ws-ch "ws://127.0.0.1:4550/oscbridge"
                                                      {:format :json}))]
            (>! ws-channel {:Address "/get-state" :Params ["root"]})
            (>! ws-channel {:Address "/get-samples" :Params []})
            (>! ws-channel {:Address "/get-synths" :Params []})
            (go-loop []
              (let [ns (<! set-state-ch)
                    grid (get-in @cursor [:grids ns])
                    json (js/JSON.stringify (clj->js grid))
                    update #js {:Address "/set-state" :Params #js [ns json]}]
                (go (>! ws-channel update)))
              (recur))
            (go-loop []
              (let [ns (<! get-state-ch)
                    request #js {:Address "/get-state" :Params #js [ns]}]
                (go (>! ws-channel request)))
              (recur))
            (go-loop []
              (let [{:keys [message error] :as msg} (<! ws-channel)
                    params (get message "Params")]
                (cond 
                  (= "/state" (get message "Address"))
                  (let [grid (js->clj (js/JSON.parse (second params)))]
                    (om/transact! cursor :grids #(assoc % (first params) grid)))
                  (= "/samples" (get message "Address"))
                  (let [samples (js->clj (js/JSON.parse (first params)))]
                    (om/transact! cursor :samples #(into % samples)))
                  (= "/synths" (get message "Address"))
                  (let [synths (js->clj (js/JSON.parse (first params)))]
                    (om/transact! cursor :synths #(into % synths))))
                (when message
                  (recur))))))
        {:set-state-ch set-state-ch
         :get-state-ch get-state-ch}))
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:style #js {:fontFamily "monospace"}}
               (om/build grid-view "root" {:state state})))))
        
(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
