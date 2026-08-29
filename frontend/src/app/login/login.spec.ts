import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { LoginComponent } from './login';
import { AuthService } from '../../shared/service/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            loading: signal(false),
            erro: signal<string | undefined>(undefined),
            doLogin: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
