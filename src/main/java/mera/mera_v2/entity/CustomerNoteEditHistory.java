package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "customer_note_edit_history")
public class CustomerNoteEditHistory {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "note_id", length = 64, nullable = false)
    private String noteId;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "images", columnDefinition = "JSON")
    private String images;

    @Column(name = "created_by_id", length = 64)
    private String createdById;

    @Column(name = "created_by_name", length = 255)
    private String createdByName;

    @Column(name = "created_by_pancake_id", length = 64)
    private String createdByPancakeId;

    @Column(name = "created_by_token", length = 255)
    private String createdByToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", referencedColumnName = "id", insertable = false, updatable = false)
    private CustomerNote note;
}