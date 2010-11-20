(ns clostache.parser
  "A parser for mustache templates."
  (:use [clojure.contrib.string :only (map-str)]))

(defrecord Section [name body start end])

(defn- replace-all
  "Applies all replacements from the replacement list to the string."
  [string replacements]
  (reduce (fn [string [from to]]
            (.replaceAll string from to)) string replacements))

(defn- escape-html
  "Replaces angle brackets with the respective HTML entities."
  [string]
  (replace-all string [["&" "&amp;"]
                       ["\"" "&quot;"]
                       ["<" "&lt;"]
                       [">" "&gt;"]]))

(defn- create-variable-replacements
  "Creates pairs of variable replacements from the data."
  [data]
  (apply concat
         (for [k (keys data)]
           (let [var-name (name k)
                 var-value (k data)]
             (if (instance? String var-value)
               [[(str "\\{\\{\\{\\s*" var-name "\\s*\\}\\}\\}") var-value]
                [(str "\\{\\{\\&s*" var-name "\\s*\\}\\}") var-value]
                [(str "\\{\\{\\s*" var-name "\\s*\\}\\}")
                 (escape-html var-value)]])))))

(defn- remove-comments
  "Removes comments from the template."
  [template]
  (replace-all template [["\\{\\{\\![^\\}]*\\}\\}" ""]]))

(defn- extract-section
  "Extracts the outer section from the template."
  [template]
  (let [start (.indexOf template "{{#")
        end-tag (.indexOf template "{{/" start)
        end (+ (.indexOf template "}}" end-tag) 2)]
    (if (or (= start -1) (= end 1))
      nil
      (let [section (.substring template start end)
            body-start (+ (.indexOf section "}}") 2)
            body-end (.lastIndexOf section "{{")
            body (.substring section body-start body-end)
            section-name (.trim (.substring section 3 (- body-start 2)))]
        (Section. section-name body start end)))))

(defn render
  "Renders the template with the data."
  [template data]
  (let [replacements (create-variable-replacements data)
        template (remove-comments template)
        section (extract-section template)]
    (if (nil? section)
      (replace-all template replacements)
      (let [before (.substring template 0 (:start section))
            after (.substring template (:end section))
            section-data ((keyword (:name section)) data)]
        (str (replace-all before replacements)
             (if (vector? section-data)
               (map-str (fn [m] (render (:body section) m)) section-data)
               (if section-data
                 (replace-all (:body section) replacements)))
             (replace-all after replacements))))))