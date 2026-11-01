/* ANB Network Booster - Quality Intelligence */

(function () {
    "use strict";

    function validNumber(value) {
        return typeof value === "number" &&
            Number.isFinite(value);
    }

    function clamp(value, min, max) {
        return Math.max(min, Math.min(max, value));
    }

    function component(value, weight) {
        if (!validNumber(value)) {
            return null;
        }

        return {
            score: clamp(value, 0, 100),
            weight: weight
        };
    }

    function calculateWeightedScore(items) {
        let total = 0;
        let availableWeight = 0;

        items.forEach(function (item) {
            if (item !== null) {
                total += item.score * item.weight;
                availableWeight += item.weight;
            }
        });

        if (availableWeight === 0) {
            return null;
        }

        return Math.round(total / availableWeight);
    }

    function rsrpScore(value) {
        if (!validNumber(value)) return null;
        return clamp(((value + 120) / 50) * 100, 0, 100);
    }

    function rsrqScore(value) {
        if (!validNumber(value)) return null;
        return clamp(((value + 20) / 17) * 100, 0, 100);
    }

    function sinrScore(value) {
        if (!validNumber(value)) return null;
        return clamp(((value + 5) / 35) * 100, 0, 100);
    }

    function latencyScore(value) {
        if (!validNumber(value)) return null;
        return clamp(100 - (value / 5), 0, 100);
    }

    function jitterScore(value) {
        if (!validNumber(value)) return null;
        return clamp(100 - (value * 2), 0, 100);
    }

    function speedScore(value, reference) {
        if (!validNumber(value) || value < 0) return null;
        return clamp((value / reference) * 100, 0, 100);
    }

    function grade(score) {
        if (score === null) return "Insufficient data";
        if (score >= 85) return "Excellent";
        if (score >= 70) return "Good";
        if (score >= 50) return "Fair";
        if (score >= 30) return "Weak";
        return "Poor";
    }

    function analyze(metrics) {
        const data = metrics || {};

        const signal = calculateWeightedScore([
            component(rsrpScore(data.rsrp), 30),
            component(rsrqScore(data.rsrq), 35),
            component(sinrScore(data.sinr), 35)
        ]);

        const internet = calculateWeightedScore([
            component(latencyScore(data.pingMs), 30),
            component(jitterScore(data.jitterMs), 20),
            component(speedScore(data.downloadMbps, 100), 35),
            component(speedScore(data.uploadMbps, 30), 15)
        ]);

        const combined = calculateWeightedScore([
            component(signal, 55),
            component(internet, 45)
        ]);

        const diagnoses = [];

        if (signal !== null && signal < 40) {
            diagnoses.push("Weak radio signal detected.");
        }

        if (validNumber(data.pingMs) && data.pingMs > 150) {
            diagnoses.push("High network latency detected.");
        }

        if (validNumber(data.jitterMs) && data.jitterMs > 40) {
            diagnoses.push("Connection latency is unstable.");
        }

        if (
            signal !== null &&
            signal >= 60 &&
            internet !== null &&
            internet < 40
        ) {
            diagnoses.push(
                "Signal looks reasonable, but internet performance is low."
            );
        }

        if (diagnoses.length === 0) {
            diagnoses.push(
                "Not enough evidence for a specific network diagnosis."
            );
        }

        return {
            signalScore: signal,
            internetScore: internet,
            combinedScore: combined,
            signalGrade: grade(signal),
            internetGrade: grade(internet),
            overallGrade: grade(combined),
            diagnoses: diagnoses,
            scoreType: "App-defined estimate",
            generatedAt: new Date().toISOString()
        };
    }

    window.ANBQuality = Object.freeze({
        analyze: analyze,
        grade: grade
    });

})();
