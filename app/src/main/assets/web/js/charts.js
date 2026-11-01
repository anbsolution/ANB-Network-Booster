/* ANB Network Booster - SVG Charts */

(function () {
    "use strict";

    const SVG_NS = "http://www.w3.org/2000/svg";

    function createSvgElement(name, attributes) {
        const element = document.createElementNS(SVG_NS, name);

        Object.keys(attributes || {}).forEach(function (key) {
            element.setAttribute(key, String(attributes[key]));
        });

        return element;
    }

    function renderLineChart(container, values, options) {
        if (typeof container === "string") {
            container = document.getElementById(container);
        }

        if (!container || !Array.isArray(values)) {
            return false;
        }

        const points = values.filter(function (value) {
            return typeof value === "number" &&
                Number.isFinite(value);
        });

        container.replaceChildren();

        if (points.length < 2) {
            const empty = document.createElement("p");
            empty.className = "empty-state";
            empty.textContent = "Not enough history data to draw chart.";
            container.appendChild(empty);
            return false;
        }

        const config = options || {};
        const width = 320;
        const height = 180;
        const padding = 24;

        const min = Math.min.apply(null, points);
        const max = Math.max.apply(null, points);
        const range = max - min || 1;

        const svg = createSvgElement("svg", {
            viewBox: "0 0 " + width + " " + height,
            width: "100%",
            height: "180",
            role: "img",
            "aria-label": config.label || "Network history chart"
        });

        const axis = createSvgElement("line", {
            x1: padding,
            y1: height - padding,
            x2: width - padding,
            y2: height - padding,
            stroke: "currentColor",
            opacity: ".25"
        });

        svg.appendChild(axis);

        const coordinates = points.map(function (value, index) {
            const x = padding +
                (index / (points.length - 1)) *
                (width - padding * 2);

            const y = height - padding -
                ((value - min) / range) *
                (height - padding * 2);

            return [x, y];
        });

        const pathData = coordinates.map(function (point, index) {
            return (index === 0 ? "M " : "L ") +
                point[0] + " " + point[1];
        }).join(" ");

        const line = createSvgElement("path", {
            d: pathData,
            fill: "none",
            stroke: "var(--primary)",
            "stroke-width": "3",
            "stroke-linecap": "round",
            "stroke-linejoin": "round"
        });

        svg.appendChild(line);

        coordinates.forEach(function (point) {
            svg.appendChild(createSvgElement("circle", {
                cx: point[0],
                cy: point[1],
                r: "3",
                fill: "var(--gold)"
            }));
        });

        const label = document.createElement("p");
        label.className = "card-description";
        label.textContent =
            "Minimum: " + min +
            " · Maximum: " + max;

        container.appendChild(svg);
        container.appendChild(label);

        return true;
    }

    window.ANBCharts = Object.freeze({
        renderLineChart: renderLineChart
    });

})();
