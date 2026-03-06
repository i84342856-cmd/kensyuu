package com.example.trainingspring.repository;


import com.example.trainingspring.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface  PersonRepository extends JpaRepository<Person,Long>{

}