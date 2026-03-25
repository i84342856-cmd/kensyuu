package com.example.moattravel3.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.moattravel3.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Integer> {

	public Role findByName(String name);
}
