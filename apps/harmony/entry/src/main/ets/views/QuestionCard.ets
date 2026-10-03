import { PendingUserInputRequest, PendingUserInputQuestion, PendingUserInputOption } from '@agentbuddy/core';
import { AppModel } from '../state/AppModel';
import { BuddyButton } from '../designsystem/BuddyButton';
import { BuddyColor, BuddySpacing, BuddyRadius, BuddyText } from '../designsystem/BuddyTokens';

@Component
export struct QuestionCard {
  @Prop request: PendingUserInputRequest;
  @Prop busy: boolean = false;
  @State answers: string[] = [];

  aboutToAppear(): void { this.answers = this.request.questions.map(() => ''); }

  answer(index: number, value: string): void {
    const values = this.answers.slice();
    values[index] = value;
    this.answers = values;
  }

  build() {
    Column({ space: BuddySpacing.md }) {
      Text('搭子需要你的回答').fontSize(BuddyText.heading).fontColor(BuddyColor.text).width('100%')
      ForEach(this.request.questions, (question: PendingUserInputQuestion, index: number) => {
        Column({ space: BuddySpacing.sm }) {
          Text(question.question).fontColor(BuddyColor.text).width('100%')
          ForEach(question.options, (option: PendingUserInputOption) => {
            Column({ space: BuddySpacing.xs }) {
              BuddyButton({ label: (this.answers[index] === option.label ? '✓ ' : '') + option.label,
                secondary: this.answers[index] !== option.label, isEnabled: !this.busy,
                action: () => this.answer(index, option.label) })
              if (option.description) {
                Text(option.description).fontSize(BuddyText.caption).fontColor(BuddyColor.secondary).width('100%')
              }
            }.width('100%')
          }, (option: PendingUserInputOption) => option.label)
          if (question.isOtherAllowed || question.options.length === 0) {
            TextInput({ placeholder: '输入回答', text: this.answers[index] ?? '' })
              .type(question.isSecret ? InputType.Password : InputType.Normal)
              .enabled(!this.busy).onChange((value: string) => this.answer(index, value))
          }
        }.width('100%')
      }, (question: PendingUserInputQuestion) => question.id)
      BuddyButton({ label: '提交回答', isEnabled: !this.busy && this.answers.length === this.request.questions.length &&
        this.answers.every((value: string) => value.trim().length > 0), action: () => {
          AppModel.shared.answer(this.request.id, this.request.questions.map((question: PendingUserInputQuestion, index: number) => {
            return { questionId: question.id, answers: [this.answers[index]] };
          }));
        } })
    }.padding(BuddySpacing.md).width('100%').borderRadius(BuddyRadius.card).backgroundColor(BuddyColor.surface)
  }
}
