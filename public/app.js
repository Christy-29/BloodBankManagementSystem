
const LOW_STOCK_DEFAULT = 5;

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}

async function api(url, options = {}) {
    const response = await fetch(url, options);
    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        throw new Error(data.error || "Something went wrong.");
    }

    return data;
}

// Java server expects JSON request bodies.
function formOptions(form) {
    return {
        method: "POST",
        headers: {
            "Content-Type": "application/json; charset=UTF-8"
        },
        body: JSON.stringify(
            Object.fromEntries(new FormData(form).entries())
        )
    };
}

function showMessage(id, message, isError = false) {
    const element = document.getElementById(id);

    if (!element) return;

    element.textContent = message;
    element.style.color = isError ? "#b42318" : "#16794b";
}

// ---------------- DASHBOARD ----------------

async function loadDashboard() {
    try {
        const data = await api("/api/dashboard");

        // Match the snake_case keys returned by Java.
        document.getElementById("totalDonors").textContent =
            data.total_donors ?? 0;

        document.getElementById("totalUnits").textContent =
            data.total_stock ?? 0;

        document.getElementById("pendingRequests").textContent =
            data.pending_requests ?? 0;

        document.getElementById("fulfilledRequests").textContent =
            data.fulfilled_requests ?? 0;

        // Calculate stock alerts using the same threshold as the stock section.
        const stock = await api("/api/stock");

        const outCount = stock.filter(item =>
            Number(item.units_available) === 0
        ).length;

        const lowCount = stock.filter(item => {
            const units = Number(item.units_available);
            return units > 0 && units <= LOW_STOCK_DEFAULT;
        }).length;

        document.getElementById("lowStockCount").textContent =
            `${outCount + lowCount} alert(s)`;

        const summary = document.getElementById("alertSummary");

        if (outCount > 0) {
            summary.className = "alert-summary danger";
            summary.textContent =
                `${outCount} blood group(s) are out of stock. Check the inventory below.`;
        } else if (lowCount > 0) {
            summary.className = "alert-summary warning";
            summary.textContent =
                `${lowCount} blood group(s) have ${LOW_STOCK_DEFAULT} units or fewer. Consider replenishing these groups.`;
        } else {
            summary.className = "alert-summary good";
            summary.textContent =
                `All blood groups have more than ${LOW_STOCK_DEFAULT} units available.`;
        }

        showMessage("dashboardMessage", "Dashboard updated.");

    } catch (error) {
        showMessage(
            "dashboardMessage",
            `Dashboard could not load: ${error.message}`,
            true
        );
    }
}

// ---------------- BLOOD STOCK ----------------

async function loadStock() {
    const container = document.getElementById("stockList");
    const alertList = document.getElementById("lowStockList");

    try {
        const stock = await api("/api/stock");

        if (!stock.length) {
            container.innerHTML =
                '<p class="empty-state">No blood stock records found.</p>';

            alertList.innerHTML =
                '<p class="empty-state">No stock records found.</p>';

            return;
        }

        container.innerHTML = stock.map(item => {
            const units = Number(item.units_available);

            let state = "normal";
            let label = "IN STOCK";

            if (units === 0) {
                state = "out";
                label = "OUT OF STOCK";
            } else if (units <= LOW_STOCK_DEFAULT) {
                state = "low";
                label = "LOW STOCK";
            }

            return `
                <article class="stock-card ${state}">
                    <h3>${escapeHtml(item.blood_group)}</h3>
                    <p><strong>${units}</strong> available unit(s)</p>
                    <small>${label}</small>
                </article>
            `;
        }).join("");

        const lowStock = stock.filter(item =>
            Number(item.units_available) <= LOW_STOCK_DEFAULT
        );

        if (!lowStock.length) {
            alertList.innerHTML =
                '<p class="empty-state">No low-stock blood groups. All groups are above the threshold.</p>';
        } else {
            alertList.innerHTML = lowStock.map(item => {
                const units = Number(item.units_available);
                const out = units === 0;

                return `
                    <article class="low-stock-item ${out ? "out" : ""}">
                        <h3>${escapeHtml(item.blood_group)}</h3>
                        <p><strong>${units}</strong> unit(s) remaining</p>
                        <p>${out
                            ? "Immediate stock shortage"
                            : "Replenishment recommended"}</p>
                    </article>
                `;
            }).join("");
        }

    } catch (error) {
        container.textContent =
            `Could not load stock: ${error.message}`;

        alertList.textContent =
            "Could not load low-stock alerts.";
    }
}

// ---------------- DONORS ----------------

async function loadDonors() {
    const tbody = document.getElementById("donorsTable");

    try {
        const donors = await api("/api/donors");

        if (!donors.length) {
            tbody.innerHTML =
                '<tr><td colspan="8">No donors registered yet.</td></tr>';
            return;
        }

        tbody.innerHTML = donors.map(donor => `
            <tr>
                <td>${escapeHtml(donor.donor_id)}</td>
                <td>${escapeHtml(donor.name)}</td>
                <td>${escapeHtml(donor.age)}</td>
                <td>${escapeHtml(donor.gender)}</td>
                <td>${escapeHtml(donor.phone)}</td>
                <td>${escapeHtml(donor.blood_group)}</td>
                <td>${escapeHtml(donor.city)}</td>
                <td>${escapeHtml(donor.units_donated ?? 0)}</td>
            </tr>
        `).join("");

    } catch (error) {
        tbody.innerHTML =
            `<tr><td colspan="8">${escapeHtml(error.message)}</td></tr>`;
    }
}

// ---------------- HOSPITAL REQUESTS ----------------

async function loadRequests() {
    const tbody = document.getElementById("requestsTable");

    try {
        const requests = await api("/api/requests");

        if (!requests.length) {
            tbody.innerHTML =
                '<tr><td colspan="8">No hospital requests yet.</td></tr>';
            return;
        }

        tbody.innerHTML = requests.map(request => {
            const pending =
                String(request.status).toUpperCase() === "PENDING";

            const action = pending
                ? `<button type="button"
                     data-fulfil-id="${escapeHtml(request.request_id)}">
                     Fulfil Request
                   </button>`
                : '<span class="status-pill fulfilled">Completed</span>';

            const statusClass = pending ? "" : " fulfilled";

            return `
                <tr>
                    <td>${escapeHtml(request.request_id)}</td>
                    <td>${escapeHtml(request.patient_name)}</td>
                    <td>${escapeHtml(request.hospital_name)}</td>
                    <td>${escapeHtml(request.blood_group)}</td>
                    <td>${escapeHtml(request.units_requested)}</td>
                    <td>
                        <span class="status-pill${statusClass}">
                            ${escapeHtml(request.status)}
                        </span>
                    </td>
                    <td>${escapeHtml(request.created_at)}</td>
                    <td>${action}</td>
                </tr>
            `;
        }).join("");

    } catch (error) {
        tbody.innerHTML =
            `<tr><td colspan="8">${escapeHtml(error.message)}</td></tr>`;
    }
}

// ---------------- ADD DONOR ----------------

document.getElementById("donorForm").addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        const form = event.currentTarget;

        try {
            const result = await api(
                "/api/donors",
                formOptions(form)
            );

            showMessage(
                "donorMessage",
                result.message || "Donor added successfully."
            );

            form.reset();

            await Promise.all([
                loadDonors(),
                loadDashboard()
            ]);

        } catch (error) {
            showMessage("donorMessage", error.message, true);
        }
    }
);

// ---------------- RECORD DONATION ----------------

document.getElementById("donationForm").addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        const form = event.currentTarget;

        try {
            const result = await api(
                "/api/donations",
                formOptions(form)
            );

            showMessage(
                "donationMessage",
                result.message || "Donation recorded successfully."
            );

            form.reset();

            await Promise.all([
                loadDonors(),
                loadStock(),
                loadDashboard()
            ]);

        } catch (error) {
            showMessage("donationMessage", error.message, true);
        }
    }
);

// ---------------- ADD HOSPITAL REQUEST ----------------

document.getElementById("requestForm").addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        const form = event.currentTarget;

        try {
            const result = await api(
                "/api/requests",
                formOptions(form)
            );

            showMessage(
                "requestMessage",
                result.message || "Request added successfully."
            );

            form.reset();

            await Promise.all([
                loadRequests(),
                loadDashboard()
            ]);

        } catch (error) {
            showMessage("requestMessage", error.message, true);
        }
    }
);

// ---------------- FULFIL REQUEST ----------------

document.getElementById("requestsTable").addEventListener(
    "click",
    async event => {
        const button = event.target.closest("[data-fulfil-id]");

        if (!button) return;

        const requestId = button.dataset.fulfilId;

        if (!window.confirm(
            `Fulfil request #${requestId}? The required units will be deducted from stock.`
        )) {
            return;
        }

        button.disabled = true;

        try {
            const result = await api("/api/requests/fulfil", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json; charset=UTF-8"
                },
                body: JSON.stringify({
                    request_id: requestId
                })
            });

            showMessage(
                "requestMessage",
                result.message || "Request fulfilled successfully."
            );

            await Promise.all([
                loadRequests(),
                loadStock(),
                loadDashboard()
            ]);

        } catch (error) {
            showMessage("requestMessage", error.message, true);
            button.disabled = false;
        }
    }
);

// ---------------- REFRESH ----------------

async function refreshAll() {
    await Promise.all([
        loadDashboard(),
        loadStock(),
        loadDonors(),
        loadRequests()
    ]);
}

document.getElementById("refreshAll")
    .addEventListener("click", refreshAll);

document.getElementById("refreshStock")
    .addEventListener("click", loadStock);

document.getElementById("refreshRequests")
    .addEventListener("click", loadRequests);

document.getElementById("refreshDonors")
    .addEventListener("click", loadDonors);

// Load all sections when the page opens.
refreshAll();

// Automatically refresh every 30 seconds.
window.setInterval(refreshAll, 30000);
