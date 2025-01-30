(ns overtone-ai-fun
  (:use [overtone.live])                                    ;; boots up Overtone!
  (:require [allem.core :as allem]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [overtone.inst.drum :refer :all]
            [clojure.pprint]
            [allem.util :as util]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]
            [overtone.inst.synth :as synth]))

;; avoid TOO LOUD!
(overtone.studio.mixer/volume 0.5)

(comment
  ;; is overtone working?
  (synth/tb303 :release 0.1))

(comment
  (do
    (require '[portal.api :as portal])
    (add-tap #'portal/submit)
    (def p (portal/open {:launcher :intellij}))))

(comment
  ;; do we get nice repl output?
  (tap> 'hello))

(comment
  (allem/claude
    {:msg "Give me a beat!"}))

(defonce !state (atom {:playing {:drums {:player nil
                                         :pattern nil}
                                 :bass  {:player nil
                                         :pattern nil}}}))

(def metro (metronome 128))

(defn next-bar [m & [n]]
  (let [n (or n 4)
        b (m)
        i (mod b n)]
    (+ b (- n i))))


(defn player
  [tick bar & [n]]

  (let [n (or n 4)]
    (dorun
      (for [k (keys bar)]
        (let [beat (Math/floor k)
              offset (- k beat)]
          (if (== 0 (mod (- tick beat) n))
            (let [instruments (bar k)]
              (dorun
                (for [instrument instruments]
                  (at (metro (+ offset tick)) (instrument)))))))))))


(defn drums->overtone [d]
  (->> (for [[k vs] d]
         [k (->> (mapv resolve vs)
                 (remove nil?))])
       (into {})))

(defn bass->overtone [d]
  (->> (for [[k vs] d]
         [k [#(apply synth/tb303
                     :note (first vs)
                     (rest vs))]])
       (into {})))

(def pattern-type->overtone
  {:drums drums->overtone
   :bass  bass->overtone})

(defn run-sequencer*
  [m pattern k]
  (let [beat (m)]
    #_(tap> beat)
    (player beat pattern)
    (swap! !state assoc-in [:playing k :player] (apply-by (m (inc beat)) #'run-sequencer* [m pattern k]))))

(defn current-player [k]
  (get-in @!state [:playing k :player]))

(defn run-sequencer
  [m pattern k]
  (let [beat (next-bar m)
        pattern-transformed ((get pattern-type->overtone k) pattern)]
    (swap! !state assoc-in [:playing k :pattern] pattern)
    (when-let [p (current-player k)]
      (at (m beat)
          (stop-player p)))
    (player beat pattern-transformed)
    (-> (swap! !state assoc-in [:playing k :player] (at (m (inc beat)) (apply #'run-sequencer* [m pattern-transformed k])))
        k)))

(defn current-pattern [k]
  (get-in @!state [:playing k :pattern]))

(defn add-pattern-as-example! [k pattern]
  (swap! !state update-in [:examples k] conj pattern))

;; get all instruments (definst)

(defn all-drums []
  (let [nodes (p/parse-string-all (slurp "https://raw.githubusercontent.com/overtone/overtone/master/src/overtone/inst/drum.clj"))]
    (->> (n/sexpr nodes)
         (filter coll?)
         (filter (comp #{'definst} first))
         (map second))))

(comment
  (all-drums))

(comment
  (stop))

(comment
  (do
    (run-sequencer metro
                   example-drum-pattern
                   :drums)))

(def pattern-examples
  {:drums {0   ['kick]
           0.5 ['closed-hat]
           1   ['kick 'snare]
           1.5 ['closed-hat]
           2   ['kick]
           2.5 ['closed-hat]
           3   ['kick 'snare]
           3.5 ['closed-hat]}
   :bass {0 [30 :release 0.1]
          0.5 [37]
          0.75 [30]
          1 [40]
          1.5 [54 :cutoff 500]
          2 [30]
          2.5 [30]
          3 [42 :release 0.1 :wave 2]
          3.5 [34 :cutoff 1000]
          3.75 [39 :cutoff  4000]}})

(comment
  (run-sequencer metro (get pattern-examples :drums)
                 :drums))

(comment
  (run-sequencer metro (get pattern-examples :bass)
                 :bass))

(comment
  (stop))

(defn make-example-string [k]
  (when-let [rag (get-in @!state [:examples k])]
    (format "Here are some good examples:\n\n---\n%s\n\n---"
            (->> (for [r rag]
                   (util/spprint r))
                 (str/join "\n\n")))))


(def templates
  {:drums
   (delay (str "Let's say we have an example drum-pattern in the following
structure (EDN):\n
%s
\n
Can you create a similar drum-pattern in the same format?
%s
You have the following instruments at your disposal:

" (str/join "\n" (all-drums)) "

Please respond with EDN in the following format:

{:explanation \"<string with your explanation>\"
 :pattern <beat in the above format>}

Nothing apart from the EDN is allowed in your response."))
   :bass
   (delay "Let's say we have an example bass-line in the following
structure (EDN):\n
%s
\n
- The keys are the step time (eg 0.5 means the second 1/8).
- The values are vectors which consist of the note to be played (corresponding to midi note numbers),
  and then any parameters (eg :cutoff 400)

Can you create another bass-line in the same format?
%s
You have the following parameters at your disposal:
     wave       {:default 1 :min 0 :max 2 :step 1
     r          {:default 0.8 :min 0.01 :max 0.99 :step 0.01}
     attack     {:default 0.01 :min 0.001 :max 4 :step 0.001}
     decay      {:default 0.1 :min 0.001 :max 4 :step 0.001}
     sustain    {:default 0.6 :min 0.001 :max 0.99 :step 0.001}
     release    {:default 0.01 :min 0.001 :max 4 :step 0.001}
     cutoff     {:default 100 :min 1 :max 20000 :step 1}
     env-amount {:default 0.01 :min 0.001 :max 4 :step 0.001}
     amp        {:default 0.5 :min 0 :max 1 :step 0.01}

%s

Please respond with EDN in the following format:

{:explanation \"<string with your explanation>\"
 :pattern <bass-line in the above format>}

Nothing apart from the EDN is allowed in your response.")})

(defn make-prompt
  [k & {:keys [instructions example]}]
  (format (deref (get templates k))
          (util/spprint (or example
                            (get pattern-examples k)))
          (or (some->> instructions (format "\n*** Important: %s ***\n"))
              "")
          (or (make-example-string k)
              "")))

(comment
  (make-prompt :bass))

(defn generate-and-run [k & {:keys [instructions example]}]
  (let [msg (make-prompt k
                         :instructions instructions
                         :example example)
        _ (util/tap-> msg)
        text (util/tap-> (allem/claude
                           {:msg msg
                            :model "claude-3-5-sonnet-20240620"}))
        {:keys [explanation pattern]} (edn/read-string text)
        _ (util/tap-> explanation)
        _ (util/tap-> pattern)]

    (run-sequencer metro pattern k)))

(defn stop-current [k]
  (stop-player (current-player k)))

(comment
  (generate-and-run :bass))

(comment
  (stop-current :bass))

(comment
  (add-pattern-as-example! :drums))

(comment
  (stop))



