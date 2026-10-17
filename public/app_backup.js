
const donorForm = document.getElementById("donorForm");
const donationForm = document.getElementById("donationForm");

const donorTable = document.getElementById("donorTable");
const donorCount = document.getElementById("donorCount");
const unitCount = document.getElementById("unitCount");
const stockGrid = document.getElementById("stockGrid");

const message = document.getElementById("message");
const donationMessage = document.getElementById("donationMessage");

function safe(value) {
    return String(value ?? "").replace(/[&<>"']/g, c => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[c]);
}

async function api(url, options = {}) {
    const response = await fetch(url, options);

    const result = await response.json();

    if (!response.ok) {
        throw new Error(
            result.error || "The request failed."
        );
    }

    return result;
}

async function loadDonors() {
    const donors = await api("/api/donors");

    donorCount.textContent = donors.length;

    donorTable.innerHTML = donors.length
        ? donors.map(d => `
            <tr>
                <td>${d.donor_id}</td>
                <td>${safe(d.name)}</td>
                <td>${d.age}</td>
                <td>${safe(d.blood_group)}</td>
                <td>${safe(d.phone)}</td>
                <td>${safe(d.city)}</td>
            </tr>
        `).join("")
        : `<tr>
             <td colspan="6">No donors registered yet.</td>
           </tr>`;
}

async function loadStock() {
    const stock = await api("/api/stock");

    stockGrid.innerHTML = stock.map(s => `
        <div class="stock-card">
            <strong>${safe(s.blood_group)}</strong>
            <span>${Number(s.units_available)} units available</span>
        </div>
    `).join("");

    unitCount.textContent = stock.reduce(
        (total, s) => total + Number(s.units_available), 0
    );
}

async function refreshAll() {
    await Promise.all([
        loadDonors(),
        loadStock()
    ]);
}

// Donor registration
donorForm.addEventListener("submit", async event => {
    event.preventDefault();

    const button = donorForm.querySelector(
        'button[type="submit"]'
    );

    button.disabled = true;
    message.textContent = "Saving donor...";

    try {
        const result = await api("/api/donors", {
            method: "POST",
            headers: {
                "Content-Type":
                    "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams(
                new FormData(donorForm)
            ).toString()
        });

        donorForm.reset();

        await loadDonors();

        message.textContent = result.message;

    } catch (error) {
        message.textContent = error.message;

    } finally {
        button.disabled = false;
    }
});

// Donation recording and stock update
donationForm.addEventListener("submit", async event => {
    event.preventDefault();

    const button = donationForm.querySelector(
        'button[type="submit"]'
    );

    button.disabled = true;
    donationMessage.textContent = "Recording donation...";

    try {
        const result = await api("/api/donations", {
            method: "POST",
            headers: {
                "Content-Type":
                    "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams(
                new FormData(donationForm)
            ).toString()
        });

        donationForm.reset();

        await loadStock();

        donationMessage.textContent = result.message;

    } catch (error) {
        donationMessage.textContent = error.message;

    } finally {
        button.disabled = false;
    }
});

// Load initial dashboard information
refreshAll().catch(error => {
    message.textContent =
        "Could not load dashboard: " + error.message;
});
