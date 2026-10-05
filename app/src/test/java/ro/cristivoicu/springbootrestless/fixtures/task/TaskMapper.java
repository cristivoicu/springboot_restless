package ro.cristivoicu.springbootrestless.fixtures.task;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class TaskMapper implements Mapper<Task, TaskDto> {
    @Override
    public TaskDto map(Task source) {
        TaskDto dto = new TaskDto();
        BeanUtils.copyProperties(source, dto);
        return dto;
    }
}
