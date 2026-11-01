/* ANB Network Booster - Main Application */

(function () {
    "use strict";

    const THEME_KEY = "anb-network-theme";

    function getElement(id) {
        return document.getElementById(id);
    }

    function showMessage(container, message, type) {
        if (!container) return;

        container.replaceChildren();

        const element = document.createElement("div");
        element.className = "alert " + (type || "");
        element.textContent = message;

        container.appendChild(element);
    }

    function setTheme(theme) {
        const selected = theme === "light" ? "light" : "dark";

        document.documentElement.dataset.theme = selected;

        const button = getElement("theme-toggle");

        if (button) {
            button.textContent =
                selected === "dark" ? "Light Mode" : "Dark Mode";
        }

        try {
            localStorage.setItem(THEME_KEY, selected);
        } catch (error) {
            // Theme still works for the current session.
        }
    }

    function initializeTheme() {
        let saved = "dark";

        try {
            saved = localStorage.getItem(THEME_KEY) || "dark";
        } catch (error) {
            // Use the default theme.
        }

        setTheme(saved);

        const button = getElement("theme-toggle");

        if (button) {
            button.addEventListener("click", function () {
                const current =
                    document.documentElement.dataset.theme;

                setTheme(current === "dark" ? "light" : "dark");
            });
        }
    }

    function renderNetworkSnapshot(snapshot) {
        const status = getElement("network-status");
        const summary = getElement("quality-summary");

        if (!snapshot || typeof snapshot !== "object") {
            showMessage(
                status,
                "Network information is not available yet.",
                "alert-warning"
            );
            return;
        }

        showMessage(
            status,
            "Network data received from Android.",
            "alert-success"
        );

        if (window.ANBQuality) {
            const report = window.ANBQuality.analyze(snapshot);

            if (summary) {
                summary.replaceChildren();

                const score = document.createElement("div");
                score.className = "quality-score";

                const value = document.createElement("p");
                value.className = "quality-score-value";
                value.textContent =
                    report.combinedScore === null
                        ? "--"
                        : report.combinedScore;

                const label = document.createElement("p");
                label.className = "quality-score-label";
                label.textContent =
                    report.overallGrade + " · App estimate";

                score.append(value, label);
                summary.appendChild(score);

                report.diagnoses.forEach(function (diagnosis) {
                    const item = document.createElement("div");
                    item.className = "diagnosis-card";

                    const text = document.createElement("p");
                    text.textContent = diagnosis;

                    item.appendChild(text);
                    summary.appendChild(item);
                });
            }
        }
    }

    function refreshNetwork() {
        const status = getElement("network-status");

        if (!window.ANB || !window.ANB.isNativeAvailable()) {
            showMessage(
                status,
                "Android bridge is not connected. Native integration is pending.",
                "alert-warning"
            );
            return;
        }

        window.ANB.getNetworkSnapshot()
            .then(renderNetworkSnapshot)
            .catch(function () {
                showMessage(
                    status,
                    "Unable to retrieve network information.",
                    "alert-danger"
                );
            });
    }

    function initializeSpeedTest() {
        const button = getElement("run-speed-test");

        if (!button) return;

        button.addEventListener("click", function () {
            const result = getElement("speed-results");

            if (!window.ANB || !window.ANB.isNativeAvailable()) {
                showMessage(
                    result,
                    "Native speed test is not connected yet.",
                    "alert-warning"
                );
                return;
            }

            button.disabled = true;
            button.textContent = "Testing...";

            window.ANB.runSpeedTest()
                .then(function (data) {
                    showMessage(
                        result,
                        "Speed test completed. Results received.",
                        "alert-success"
                    );

                    if (data && typeof data === "object") {
                        const details =
                            document.createElement("pre");

                        details.textContent =
                            JSON.stringify(data, null, 2);

                        result.appendChild(details);
                    }
                })
                .catch(function (error) {
                    showMessage(
                        result,
                        error.message || "Speed test failed.",
                        "alert-danger"
                    );
                })
                .finally(function () {
                    button.disabled = false;
                    button.textContent = "Run Speed Test";
                });
        });
    }

    function initializeApp() {
        initializeTheme();

        if (window.ANBNavigation) {
            window.ANBNavigation.initialize();
        }

        initializeSpeedTest();
        refreshNetwork();
    }

    document.addEventListener("DOMContentLoaded", initializeApp);

})();
