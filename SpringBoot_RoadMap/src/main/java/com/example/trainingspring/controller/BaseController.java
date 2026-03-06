package com.example.trainingspring.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.trainingspring.entity.Person;
import com.example.trainingspring.repository.PersonRepository;

@Controller
public class BaseController{

    private final PersonRepository repository;

    @Autowired
    public BaseController(PersonRepository repository){
        this.repository = repository;
    }

    @GetMapping("/")
    public String home(@ModelAttribute Person person) {
      return "form";
    }

    @PostMapping("/form")
    public String result(@Validated
                        @ModelAttribute Person person,
                        BindingResult result){
        if(result.hasErrors()){
            return "form";
        }
        repository.save(person);
        return "result";
    }
}