package com.CollegeResources.repository;

import com.CollegeResources.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    User findByEmail(String email);

    List<User> findByUserGroup(String userGroup);
    List<User> findByBatchYear(String batchYear);
    List<User> findByDepartment(String department);
    // Combined queries
    List<User> findByBatchYearAndDepartment(String batchYear, String department);
}
