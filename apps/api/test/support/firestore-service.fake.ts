export class FirestoreServiceFake {
  constructor(private ready = true) {}

  setReady(ready: boolean): void {
    this.ready = ready;
  }

  async isReady(): Promise<boolean> {
    return this.ready;
  }
}
