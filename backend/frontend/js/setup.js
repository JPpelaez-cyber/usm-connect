let currentStep = 1;
const TOTAL_STEPS = 5;
let options = null;
let selectedHobbies = new Set();
let selectedGames = new Set();
let selectedFoods = new Set();
let uploadedPhotoFile = null;

const alertBox = document.getElementById("alertBox");
const nextBtn = document.getElementById("nextBtn");
const backBtn = document.getElementById("backBtn");

function showAlert(message) {
  alertBox.innerHTML = `<div class="alert alert-error">${escapeHtml(message)}</div>`;
}
function clearAlert() { alertBox.innerHTML = ""; }

function renderStep() {
  document.querySelectorAll(".step").forEach((el) => {
    el.classList.toggle("hidden", Number(el.dataset.step) !== currentStep);
  });
  document.querySelectorAll(".step-dot").forEach((el) => {
    const n = Number(el.dataset.step);
    el.classList.toggle("done", n < currentStep);
    el.classList.toggle("current", n === currentStep);
  });
  backBtn.style.visibility = currentStep === 1 ? "hidden" : "visible";
  nextBtn.textContent = currentStep === TOTAL_STEPS ? "Finish & Start Matching" : "Next";
}

function buildChip(container, label, selectedSet) {
  const chip = document.createElement("div");
  chip.className = "chip" + (selectedSet.has(label) ? " selected" : "");
  chip.textContent = label;
  chip.addEventListener("click", () => {
    if (selectedSet.has(label)) selectedSet.delete(label);
    else selectedSet.add(label);
    chip.classList.toggle("selected");
  });
  container.appendChild(chip);
}

function buildFoodCard(container, food) {
  const card = document.createElement("div");
  card.className = "food-card" + (selectedFoods.has(food.name) ? " selected" : "");
  card.innerHTML = `
    <span class="icon">${food.icon}</span>
    <div class="name">${escapeHtml(food.name)}</div>
    <div class="desc">${escapeHtml(food.description)}</div>
  `;
  card.addEventListener("click", () => {
    if (selectedFoods.has(food.name)) selectedFoods.delete(food.name);
    else selectedFoods.add(food.name);
    card.classList.toggle("selected");
  });
  container.appendChild(card);
}

function populateCourses() {
  const collegeSelect = document.getElementById("collegeSelect");
  const courseSelect = document.getElementById("courseSelect");
  collegeSelect.innerHTML = "";
  Object.keys(options.courses).forEach((college) => {
    const opt = document.createElement("option");
    opt.value = college;
    opt.textContent = college;
    collegeSelect.appendChild(opt);
  });
  function refreshCourses() {
    courseSelect.innerHTML = "";
    const courses = options.courses[collegeSelect.value] || [];
    courses.forEach((c) => {
      const opt = document.createElement("option");
      opt.value = c;
      opt.textContent = c;
      courseSelect.appendChild(opt);
    });
  }
  collegeSelect.addEventListener("change", refreshCourses);
  refreshCourses();
}

function populateYearLevels() {
  const yearSelect = document.getElementById("yearSelect");
  yearSelect.innerHTML = "";
  options.yearLevels.forEach((y) => {
    const opt = document.createElement("option");
    opt.value = y;
    opt.textContent = y;
    yearSelect.appendChild(opt);
  });
}

async function loadOptionsAndUser() {
  options = await Api.get("/api/options");
  populateCourses();
  populateYearLevels();

  const hobbiesGrid = document.getElementById("hobbiesGrid");
  options.hobbies.forEach((h) => buildChip(hobbiesGrid, h, selectedHobbies));

  const gamesGrid = document.getElementById("gamesGrid");
  options.games.forEach((g) => buildChip(gamesGrid, g, selectedGames));

  const foodsGrid = document.getElementById("foodsGrid");
  options.foods.forEach((f) => buildFoodCard(foodsGrid, f));

  // Pre-fill with existing data if the user is editing an existing profile.
  const user = await requireAuth();
  if (!user) return;
  if (user.bio) document.getElementById("bio").value = user.bio;
  if (user.photoPath) {
    document.getElementById("photoPreview").src = user.photoPath;
    document.getElementById("photoPreview").classList.remove("hidden");
    document.getElementById("photoPlaceholder").classList.add("hidden");
  }
  if (user.college) document.getElementById("collegeSelect").value = user.college;
  document.getElementById("collegeSelect").dispatchEvent(new Event("change"));
  if (user.course) document.getElementById("courseSelect").value = user.course;
  if (user.yearLevel) document.getElementById("yearSelect").value = user.yearLevel;

  (user.hobbies || []).forEach((h) => selectedHobbies.add(h));
  (user.games || []).forEach((g) => selectedGames.add(g));
  (user.foods || []).forEach((f) => selectedFoods.add(f));
  document.querySelectorAll("#hobbiesGrid .chip").forEach((chip) => {
    if (selectedHobbies.has(chip.textContent)) chip.classList.add("selected");
  });
  document.querySelectorAll("#gamesGrid .chip").forEach((chip) => {
    if (selectedGames.has(chip.textContent)) chip.classList.add("selected");
  });
  document.querySelectorAll("#foodsGrid .food-card").forEach((card) => {
    const name = card.querySelector(".name").textContent;
    if (selectedFoods.has(name)) card.classList.add("selected");
  });
}

document.getElementById("photoUpload").addEventListener("click", () => {
  document.getElementById("photoInput").click();
});
document.getElementById("photoInput").addEventListener("change", (e) => {
  const file = e.target.files[0];
  if (!file) return;
  uploadedPhotoFile = file;
  const reader = new FileReader();
  reader.onload = (ev) => {
    const img = document.getElementById("photoPreview");
    img.src = ev.target.result;
    img.classList.remove("hidden");
    document.getElementById("photoPlaceholder").classList.add("hidden");
  };
  reader.readAsDataURL(file);
});

backBtn.addEventListener("click", () => {
  if (currentStep > 1) { currentStep--; renderStep(); clearAlert(); }
});

nextBtn.addEventListener("click", async () => {
  clearAlert();

  if (currentStep === 3 && selectedHobbies.size === 0) {
    showAlert("Pick at least one hobby so we can find your matches.");
    return;
  }

  if (currentStep < TOTAL_STEPS) {
    currentStep++;
    renderStep();
    return;
  }

  // Final step: save everything.
  nextBtn.disabled = true;
  nextBtn.textContent = "Saving...";
  try {
    if (uploadedPhotoFile) {
      await Api.uploadPhoto(uploadedPhotoFile);
    }
    await Api.put("/api/profile", {
      college: document.getElementById("collegeSelect").value,
      course: document.getElementById("courseSelect").value,
      yearLevel: document.getElementById("yearSelect").value,
      bio: document.getElementById("bio").value,
      hobbies: Array.from(selectedHobbies),
      games: Array.from(selectedGames),
      foods: Array.from(selectedFoods),
    });
    window.location.href = "/discover.html";
  } catch (err) {
    showAlert(err.message);
    nextBtn.disabled = false;
    nextBtn.textContent = "Finish & Start Matching";
  }
});

renderStep();
loadOptionsAndUser();
