package com.example.moattravel3.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.moattravel3.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    // 基本的なCRUD操作（保存、検索、削除など）はJpaRepositoryが提供するため、
    // 特殊な検索が必要なければ、中身は空のままで動作します。
	public Role findByName(String name);
}
