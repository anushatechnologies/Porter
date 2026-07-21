import os

base_pkg = "src/main/java/com/anushaporter/backend"
os.makedirs(f"{base_pkg}/model", exist_ok=True)
os.makedirs(f"{base_pkg}/repository", exist_ok=True)
os.makedirs(f"{base_pkg}/controller", exist_ok=True)

models = {
    "Order": "private String customer; private String pickup; private String dropoff; private String type; private String status; private String driver; private String price; private String distance;",
    "Driver": "private String name; private String phone; private String vehicle; private String rating; private String status; private String location;",
    "Customer": "private String name; private String phone; private String email; private Double wallet; private Integer totalOrders;",
    "Vehicle": "private String model; private String plate; private String owner; private String type; private Integer trips; private String capacity;",
    "Ticket": "private String customerName; private String driverName; private String issue; private String priority; private String status;",
    "Notification": "private String title; private String message; private String audience; private String target; private Boolean readStatus;",
    "Payout": "private String driver; private String amount; private String trips; private String type; private String status;",
    "Franchise": "private String name; private String head; private String address; private Integer driversCount; private Integer dailyOrders; private String revenue; private String status;",
    "AppUser": "private String name; private String email; private String role; private String status;"
}

for entity, fields in models.items():
    with open(f"{base_pkg}/model/{entity}.java", "w") as f:
        f.write(f'''package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "{entity.lower()}s")
public class {entity} {{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    {fields}
}}
''')

    with open(f"{base_pkg}/repository/{entity}Repository.java", "w") as f:
        f.write(f'''package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.{entity};
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface {entity}Repository extends JpaRepository<{entity}, Long> {{
}}
''')

    with open(f"{base_pkg}/controller/{entity}Controller.java", "w") as f:
        f.write(f'''package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.{entity};
import com.anushaporter.backend.repository.{entity}Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/{entity.lower()}s")
@CrossOrigin(origins = "*")
public class {entity}Controller {{
    @Autowired
    private {entity}Repository repository;

    @GetMapping
    public List<{entity}> getAll() {{
        return repository.findAll();
    }}

    @PostMapping
    public {entity} create(@RequestBody {entity} entity) {{
        return repository.save(entity);
    }}
}}
''')

print("Backend scaffolded successfully.")
