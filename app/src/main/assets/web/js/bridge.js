/* ANB Network Booster - Android Bridge Adapter */

(function () {
    "use strict";

    const REQUEST_TIMEOUT = 30000;
    const pendingRequests = new Map();
    let requestCounter = 0;

    function getNativeBridge() {
        return window.AndroidBridge || null;
    }

    function isAvailable() {
        const bridge = getNativeBridge();

        return !!(
            bridge &&
            typeof bridge.getNetworkSnapshot === "function" &&
            typeof bridge.getSignalHistory === "function" &&
            typeof bridge.startSpeedTest === "function"
        );
    }

    function parseResponse(response) {
        if (typeof response === "string") {
            try {
                return JSON.parse(response);
            } catch (error) {
                throw new Error("Invalid native response format");
            }
        }

        if (!response || typeof response !== "object") {
            throw new Error("Empty native response");
        }

        return response;
    }

    function callNative(methodName) {
        const bridge = getNativeBridge();

        if (!bridge || typeof bridge[methodName] !== "function") {
            return Promise.reject(
                new Error("Android native bridge unavailable")
            );
        }

        try {
            return Promise.resolve(
                parseResponse(bridge[methodName]())
            );
        } catch (error) {
            return Promise.reject(error);
        }
    }

    function createRequestId() {
        requestCounter += 1;

        return "anb_" + Date.now() + "_" + requestCounter;
    }

    function startSpeedTest() {
        const bridge = getNativeBridge();

        if (!bridge || typeof bridge.startSpeedTest !== "function") {
            return Promise.reject(
                new Error("Speed test bridge unavailable")
            );
        }

        const requestId = createRequestId();

        return new Promise(function (resolve, reject) {
            const timeout = setTimeout(function () {
                pendingRequests.delete(requestId);
                reject(new Error("Speed test request timed out"));
            }, REQUEST_TIMEOUT);

            pendingRequests.set(requestId, {
                resolve: resolve,
                reject: reject,
                timeout: timeout
            });

            try {
                bridge.startSpeedTest(requestId);
            } catch (error) {
                clearTimeout(timeout);
                pendingRequests.delete(requestId);
                reject(error);
            }
        });
    }

    function handleNativeResult(requestId, responseJson) {
        const request = pendingRequests.get(requestId);

        if (!request) {
            return;
        }

        clearTimeout(request.timeout);
        pendingRequests.delete(requestId);

        try {
            const result = parseResponse(responseJson);

            if (result.error) {
                request.reject(new Error(String(result.error)));
            } else {
                request.resolve(result);
            }
        } catch (error) {
            request.reject(error);
        }
    }

    window.ANB = Object.freeze({
        isNativeAvailable: isAvailable,

        getNetworkSnapshot: function () {
            return callNative("getNetworkSnapshot");
        },

        getSignalHistory: function () {
            return callNative("getSignalHistory");
        },

        runSpeedTest: startSpeedTest,

        onNativeResult: handleNativeResult
    });

})();
