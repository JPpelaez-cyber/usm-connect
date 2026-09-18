package com.usmconnect.config;

import com.usmconnect.util.JsonUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Loads the selectable options (courses, statuses, hobbies, games, foods)
 * from data/options.json. If the file doesn't exist yet, a sensible
 * USM-focused default is written out on first run.
 *
 * This file IS the "admin" layer for now: to add/remove/edit a course,
 * hobby, game or food, edit backend/data/options.json directly (no code
 * changes, no rebuild) and restart the server, or POST to /api/admin/options
 * (protected by ADMIN_KEY, see AdminHandler) to update it live.
 */
public final class OptionsConfig {

    private final Path filePath;
    private Map<String, Object> data;

    public OptionsConfig(Path filePath) {
        this.filePath = filePath;
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            if (!Files.exists(filePath)) {
                writeDefaults();
            }
            String json = new String(Files.readAllBytes(filePath), "UTF-8");
            data = JsonUtil.parseObject(json);
        } catch (IOException e) {
            throw new RuntimeException("Could not load options.json", e);
        }
    }

    public synchronized Map<String, Object> asMap() {
        return data;
    }

    public synchronized void update(Map<String, Object> newData) {
        this.data = newData;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, JsonUtil.write(data).getBytes("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException("Could not save options.json", e);
        }
    }

    private void writeDefaults() throws IOException {
        Map<String, Object> defaults = new LinkedHashMap<>();

        // Courses organized by college, matching USM's actual official structure
        // (see https://www.usm.edu.ph/academics/colleges/).
        Map<String, Object> courses = new LinkedHashMap<>();
        courses.put("College of Agriculture", list(
                "BS Agriculture", "BS Fisheries", "BS Agricultural Technology"));
        courses.put("College of Arts & Social Sciences", list(
                "AB Psychology", "AB English Language", "AB Political Science",
                "AB Philosophy (Pre-Law)", "BS Criminology"));
        courses.put("College of Education", list(
                "Bachelor of Elementary Education",
                "Bachelor of Secondary Education - English",
                "Bachelor of Secondary Education - Filipino",
                "Bachelor of Secondary Education - Social Studies",
                "Bachelor of Secondary Education - Science Mathematics"));
        courses.put("College of Engineering and Information Technology", list(
                "BS Agricultural and Biosystems Engineering", "BS Civil Engineering",
                "BS Computer Engineering", "BS Computer Science", "BS Electronics Engineering",
                "BS Information Systems", "Bachelor of Library & Information Science"));
        courses.put("College of Human Ecology and Food Sciences", list(
                "BS Food Technology", "BS Hospital Management", "BS Nutrition and Dietetics",
                "BS Tourism Management"));
        courses.put("College of Industry and Technology", list(
                "BTVTE - Automotive Technology", "BTVTE - Drafting Technology",
                "BTVTE - Electrical Technology", "BTVTE - Electronics Technology",
                "BS Industrial Technology - Architectural Drafting Technology",
                "BS Industrial Technology - Automotive Technology",
                "BS Industrial Technology - Electronics Technology",
                "BS Industrial Technology - Electrical Technology"));
        courses.put("College of Health Sciences", list("BS Nursing"));
        courses.put("College of Business, Development Economics and Management", list(
                "BS Accountancy", "BS Management Accounting", "BS Agribusiness",
                "BS Agricultural Economics", "BS Development Management",
                "BS Business Administration"));
        courses.put("Institute of Middle East & Asian Studies", list(
                "BS International Relations", "Bachelor of Arts in Islamic Studies"));
        courses.put("Institute of Physical Education and Recreation", list(
                "Bachelor of Physical Education",
                "BS Exercise and Sports Sciences - Fitness and Sports Coaching",
                "BS Exercise and Sports Sciences - Fitness and Sports Management"));
        courses.put("College of Science & Mathematics", list(
                "BS Chemistry", "BS Biology", "BS Development Communication",
                "BS Applied Mathematics"));
        courses.put("College of Veterinary Medicine", list(
                "Doctor of Veterinary Medicine", "BS Veterinary Technology",
                "Veterinary Aide (1 year)"));
        defaults.put("courses", courses);

        defaults.put("yearLevels", list(
                "1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year",
                "Graduate Student", "Alumni / Graduate", "Faculty / Staff"));

        defaults.put("hobbies", list(
                "Basketball", "Volleyball", "Gaming", "Music", "Movies", "Reading",
                "Photography", "Traveling", "Programming", "Sports", "Art", "Cooking",
                "Fitness / Gym", "Anime", "Dancing", "Singing", "Studying Together"));

        defaults.put("games", list(
                "Mobile Legends: Bang Bang", "Call of Duty Mobile", "Roblox", "Minecraft",
                "Valorant", "League of Legends", "Genshin Impact", "PUBG Mobile",
                "Among Us", "Dota 2", "Chess", "EA Sports FC"));

        // Filipino foods -- no pork or pork-derived dishes, since a good share
        // of the USM student body is Muslim. icon = emoji shown on the
        // selectable card (see README for how to swap these for real
        // licensed photos in your own deployment).
        List<Object> foods = new ArrayList<>();
        foods.add(food("Chicken Adobo", "🍗", "Chicken braised in soy sauce, vinegar & garlic"));
        foods.add(food("Sinigang", "🍲", "Sour tamarind soup with fish, shrimp or beef"));
        foods.add(food("Kare-Kare", "🥜", "Oxtail & vegetables in rich peanut sauce"));
        foods.add(food("Pancit", "🍜", "Stir-fried noodles for long life & birthdays"));
        foods.add(food("Lumpia", "🥟", "Crispy Filipino spring rolls"));
        foods.add(food("Tinola", "🍛", "Ginger chicken soup with papaya & chili leaves"));
        foods.add(food("Chicken Inasal", "🍗", "Grilled chicken marinated in calamansi & annatto"));
        foods.add(food("Halo-Halo", "🍧", "Shaved ice dessert loaded with sweet mix-ins"));
        foods.add(food("Beef Tapsilog", "🍳", "Beef tapa, garlic rice & fried egg"));
        foods.add(food("Bulalo", "🍖", "Beef shank & marrow bone soup"));
        foods.add(food("Beef Caldereta", "🍅", "Beef or goat stew in rich tomato sauce"));
        foods.add(food("Chicken Curry", "🍛", "Filipino-style chicken curry with potatoes & carrots"));
        foods.add(food("Ginataang Manok", "🥥", "Chicken simmered in coconut milk"));
        foods.add(food("Beef Mechado", "🍖", "Beef stew in tomato-soy sauce"));
        defaults.put("foods", foods);

        data = defaults;
        save();
    }

    private static List<Object> list(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static Map<String, Object> food(String name, String icon, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("icon", icon);
        m.put("description", desc);
        return m;
    }
}
